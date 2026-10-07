#!/usr/bin/env bash
set -euo pipefail
set -x
[[ $(id -un) == hadoop ]] || { echo '请使用 hadoop 用户运行' >&2; exit 1; }
export HADOOP_HOME=${HADOOP_HOME:-/usr/local/hadoop}
export PATH="$HADOOP_HOME/bin:$PATH"
"$HADOOP_HOME/bin/hdfs" --daemon start namenode
"$HADOOP_HOME/bin/hdfs" --daemon start datanode
for ((n=0; n<60; n++)); do
  if hdfs dfsadmin -report 2>/dev/null | grep -q 'Live datanodes (1)'; then break; fi
  sleep 2
done
hdfs dfsadmin -safemode wait
hdfs dfs -mkdir -p /user/hadoop
hdfs dfs -mkdir /user/hadoop/test
hdfs dfs -ls /user/hadoop
hdfs dfs -put "$HOME/.bashrc" /user/hadoop/test/
hdfs dfs -ls /user/hadoop/test
hdfs dfs -get /user/hadoop/test "$HADOOP_HOME/"
