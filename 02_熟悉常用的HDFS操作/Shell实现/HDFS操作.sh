#!/usr/bin/env bash
op=$1
shift
case "$op" in
  upload)
    hdfs dfs -mkdir -p "$(dirname "$2")"
    if hdfs dfs -test -e "$2"; then
      read -r -p '请选择 append 或 overwrite: ' mode
      if [ "$mode" = append ]; then
        hdfs dfs -appendToFile "$1" "$2"
      else
        hdfs dfs -put -f "$1" "$2"
      fi
    else
      hdfs dfs -put "$1" "$2"
    fi
    ;;
  download)
    mkdir -p "$2"
    name=$(basename "$1")
    dest="$2/$name"
    number=1
    while [ -e "$dest" ]; do
      dest="$2/${number}_$name"
      number=$((number+1))
    done
    hdfs dfs -get "$1" "$dest"
    ;;
  cat)
    hdfs dfs -cat "$1"
    ;;
  stat)
    hdfs dfs -ls -d "$1"
    ;;
  list)
    hdfs dfs -ls -R "$1" | awk 'substr($1,1,1)=="-"'
    ;;
  create)
    hdfs dfs -mkdir -p "$(dirname "$1")"
    hdfs dfs -touchz "$1"
    ;;
  delete)
    hdfs dfs -rm "$1"
    ;;
  mkdir)
    hdfs dfs -mkdir -p "$1"
    ;;
  rmdir)
    hdfs dfs -rmdir "$1"
    ;;
  append)
    if [ "$3" = head ]; then
      temp_file=$(mktemp)
      cat "$2" > "$temp_file"
      hdfs dfs -cat "$1" >> "$temp_file"
      hdfs dfs -put -f "$temp_file" "$1"
      rm "$temp_file"
    else
      hdfs dfs -appendToFile "$2" "$1"
    fi
    ;;
  move)
    hdfs dfs -mkdir -p "$(dirname "$2")"
    hdfs dfs -mv "$1" "$2"
    ;;
esac
