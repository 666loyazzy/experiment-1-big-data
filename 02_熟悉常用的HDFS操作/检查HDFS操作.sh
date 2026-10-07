#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
root=/user/hadoop/lab1-check-$(basename "$work")
javaop() { hadoop jar target/lab1.jar lab.HdfsOperations "$@"; }
shellop() { bash "02_熟悉常用的HDFS操作/Shell实现/HDFS操作.sh" "$@"; }
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
hdfs dfs -rm -r "$root"
rm -rf -- "$work"
echo 'HDFS_CHECKS_PASS'
