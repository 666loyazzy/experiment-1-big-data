#!/usr/bin/env bash
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
hadoop jar "$repo_dir/target/lab1.jar" lab.MergeDedup "$@"
