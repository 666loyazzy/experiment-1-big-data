#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
hadoop jar "$repo_dir/target/lab1.jar" lab.HdfsOperations "$@"
