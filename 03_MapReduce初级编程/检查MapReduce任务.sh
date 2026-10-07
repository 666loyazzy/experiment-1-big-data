#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
root=/user/hadoop/lab1-mapreduce-check-$(basename "$work")
part="03_MapReduce初级编程"
hdfs dfs -mkdir -p "$root/input"
folders=(01_文件合并与去重 02_整数排序 03_祖孙关系挖掘)
classes=(MergeDedup IntegerSort GrandparentRelations)
expected=(C.txt 排序结果.txt grandchild-grandparent.txt)
for i in 0 1 2; do
  folder=${folders[$i]}
  hdfs dfs -mkdir -p "$root/input/$i"
  hdfs dfs -put "$part/$folder/输入/"* "$root/input/$i/"
  hadoop jar target/lab1.jar "lab.${classes[$i]}" -Dmapreduce.framework.name=local "$root/input/$i" "$root/output/$i"
  hdfs dfs -cat "$root/output/$i/part-r-00000" | tr '\t' ' ' > "$work/actual"
  if [[ $i == 2 ]]; then
    LC_ALL=C sort "$work/actual" > "$work/sorted-actual"
    LC_ALL=C sort "$part/$folder/预期输出/${expected[$i]}" > "$work/expected"
    cmp "$work/sorted-actual" "$work/expected"
  else
    cmp "$work/actual" "$part/$folder/预期输出/${expected[$i]}"
  fi
  echo "$folder SAMPLE_PASS"
done
hdfs dfs -mkdir -p "$root/input/edge-sort"
printf '0\n-10\n5\n5\n9223372036854775807\n-9223372036854775808\n' > "$work/edge.txt"
hdfs dfs -put "$work/edge.txt" "$root/input/edge-sort/"
hadoop jar target/lab1.jar lab.IntegerSort -Dmapreduce.framework.name=local "$root/input/edge-sort" "$root/output/edge-sort"
hdfs dfs -cat "$root/output/edge-sort/part-r-00000" | tr '\t' ' ' > "$work/actual"
printf '1 -9223372036854775808\n2 -10\n3 0\n4 5\n5 5\n6 9223372036854775807\n' > "$work/expected"
cmp "$work/actual" "$work/expected"
printf 'invalid\n' > "$work/invalid.txt"
hdfs dfs -put "$work/invalid.txt" "$root/input/invalid.txt"
if hadoop jar target/lab1.jar lab.IntegerSort -Dmapreduce.framework.name=local "$root/input/invalid.txt" "$root/output/invalid"; then
  echo '非法整数未被拒绝' >&2
  exit 1
fi
hdfs dfs -rm -r "$root"
rm -rf -- "$work"
echo 'MAPREDUCE_CHECKS_PASS'
