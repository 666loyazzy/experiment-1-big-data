#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 ]]; then
  echo '用法: bash 运行.sh HDFS输入路径 [HDFS输入路径...] HDFS输出目录' >&2
  exit 2
fi
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
hadoop jar "$repo_dir/target/lab1.jar" lab.MergeDedup "$@"
