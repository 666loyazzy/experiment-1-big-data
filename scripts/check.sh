#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p results
work=$(mktemp -d)
root=/user/hadoop/lab1-check-$(basename "$work")
javaop() { hadoop jar target/lab1.jar lab.HdfsOperations "$@"; }
shellop() { bash scripts/hdfs_operations.sh "$@"; }
check_ops() {
  impl=$1
  base=$root/$impl
  printf 'original\n' > "$work/original.txt"
  printf 'tail\n' > "$work/tail.txt"
  printf 'head\n' > "$work/head.txt"
  printf 'head\noriginal\ntail\ntail\n' > "$work/expected.txt"
  "$impl" upload "$work/original.txt" "$base/nested/item.txt" overwrite
  "$impl" upload "$work/tail.txt" "$base/nested/item.txt" append
  "$impl" append "$base/nested/item.txt" "$work/head.txt" head
  "$impl" append "$base/nested/item.txt" "$work/tail.txt" tail
  "$impl" cat "$base/nested/item.txt" > "$work/actual.txt"
  cmp "$work/expected.txt" "$work/actual.txt"
  "$impl" stat "$base/nested/item.txt"
  "$impl" list "$base"
  "$impl" download "$base/nested/item.txt" "$work/$impl"
  "$impl" download "$base/nested/item.txt" "$work/$impl"
  cmp "$work/expected.txt" "$work/$impl/item.txt"
  cmp "$work/expected.txt" "$work/$impl/item(1).txt"
  "$impl" create "$base/new/empty.txt"
  if "$impl" create "$base/new/empty.txt"; then echo '创建操作覆盖了已有文件' >&2; exit 1; fi
  "$impl" mkdir "$base/empty/child"
  "$impl" rmdir "$base/empty/child"
  if "$impl" rmdir "$base/nested"; then echo '非空目录被删除' >&2; exit 1; fi
  hdfs dfs -test -f "$base/nested/item.txt"
  "$impl" move "$base/nested/item.txt" "$base/moved/item.txt"
  if "$impl" move "$base/new/empty.txt" "$base/moved/item.txt"; then echo '移动操作覆盖了已有文件' >&2; exit 1; fi
  "$impl" upload "$work/original.txt" "$base/moved/item.txt" overwrite
  "$impl" cat "$base/moved/item.txt" > "$work/overwrite.txt"
  cmp "$work/original.txt" "$work/overwrite.txt"
  "$impl" delete "$base/moved/item.txt"
  "$impl" delete "$base/new/empty.txt"
  "$impl" rmdir "$base/nested"
  "$impl" rmdir "$base/moved"
  echo "$impl PASS"
}
check_ops javaop
check_ops shellop
hdfs dfs -mkdir -p "$root/input"
hdfs dfs -put data/dedup data/sort data/family "$root/input/"
for task in dedup sort family; do
  hadoop jar target/lab1.jar lab.MapReduceJobs -Dmapreduce.framework.name=local "$task" "$root/input/$task" "$root/output/$task"
  hdfs dfs -cat "$root/output/$task/part-r-00000" > "results/$task.txt"
  if [[ $task == family ]]; then
    tr '\t' ' ' < "results/$task.txt" | LC_ALL=C sort > "$work/actual"
    LC_ALL=C sort data/expected/family.txt > "$work/expected"
    cmp "$work/actual" "$work/expected"
  else
    tr '\t' ' ' < "results/$task.txt" > "$work/actual"
    cmp "$work/actual" "data/expected/$task.txt"
  fi
  echo "$task SAMPLE_PASS"
done
hdfs dfs -mkdir -p "$root/input/edge-sort"
printf '0\n-10\n5\n5\n9223372036854775807\n-9223372036854775808\n' > "$work/edge.txt"
hdfs dfs -put "$work/edge.txt" "$root/input/edge-sort/"
hadoop jar target/lab1.jar lab.MapReduceJobs -Dmapreduce.framework.name=local sort "$root/input/edge-sort" "$root/output/edge-sort"
hdfs dfs -cat "$root/output/edge-sort/part-r-00000" | tr '\t' ' ' > "$work/actual"
printf '1 -9223372036854775808\n2 -10\n3 0\n4 5\n5 5\n6 9223372036854775807\n' > "$work/expected"
cmp "$work/actual" "$work/expected"
echo 'SORT_EDGE_PASS'
printf 'invalid\n' > "$work/invalid.txt"
hdfs dfs -put "$work/invalid.txt" "$root/input/invalid.txt"
if hadoop jar target/lab1.jar lab.MapReduceJobs -Dmapreduce.framework.name=local sort "$root/input/invalid.txt" "$root/output/invalid"; then
  echo '非法整数未被拒绝' >&2
  exit 1
fi
echo 'INVALID_INTEGER_REJECTED'
hdfs dfs -rm -r "$root"
rm -rf -- "$work"
echo 'ALL_CHECKS_PASS'
