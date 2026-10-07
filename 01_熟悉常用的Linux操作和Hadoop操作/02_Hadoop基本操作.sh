#!/usr/bin/env bash
export HADOOP_HOME=/usr/local/hadoop
export PATH="$HADOOP_HOME/bin:$PATH"
hdfs --daemon start namenode
hdfs --daemon start datanode
hdfs dfsadmin -safemode wait
hdfs dfs -mkdir -p /user/hadoop
hdfs dfs -mkdir /user/hadoop/test
hdfs dfs -ls /user/hadoop
hdfs dfs -put "$HOME/.bashrc" /user/hadoop/test/
hdfs dfs -ls /user/hadoop/test
hdfs dfs -get /user/hadoop/test "$HADOOP_HOME/"
