#!/usr/bin/env bash
set -euo pipefail
op=${1:?缺少命令}; shift
file() { hdfs dfs -test -f "$1" || { echo "不是 HDFS 文件: $1" >&2; exit 1; }; }
case "$op" in
  upload)
    [[ $# == 2 || $# == 3 ]] || exit 2
    local_file=$1; dest=$2; mode=${3:-overwrite}
    if [[ $# == 2 ]] && hdfs dfs -test -e "$dest"; then
      read -r -p '目标已存在，请输入 append 或 overwrite: ' mode
    fi
    [[ $mode == append || $mode == overwrite ]] || exit 2
    [[ -f "$local_file" ]] || exit 1
    hdfs dfs -mkdir -p "$(dirname -- "$dest")"
    if hdfs dfs -test -e "$dest"; then
      file "$dest"
      if [[ $mode == append ]]; then hdfs dfs -appendToFile "$local_file" "$dest"
      else hdfs dfs -put -f "$local_file" "$dest"; fi
    else hdfs dfs -put "$local_file" "$dest"; fi
    ;;
  download)
    [[ $# == 2 ]] || exit 2
    file "$1"
    mkdir -p -- "$2"
    name=$(basename -- "$1"); stem=$name; ext=
    if [[ $name == *.* && $name != .* ]]; then stem=${name%.*}; ext=.${name##*.}; fi
    temp=$(mktemp "$2/.download.XXXXXX")
    trap 'rm -f -- "$temp"' EXIT
    hdfs dfs -get -f "$1" "$temp"
    n=0
    while :; do
      dest=$2/$name
      [[ $n == 0 ]] || dest=$2/$stem\($n\)$ext
      if ln -- "$temp" "$dest" 2>/dev/null; then echo "$dest"; break; fi
      [[ -e "$dest" || -L "$dest" ]] || { echo "无法创建下载文件: $dest" >&2; exit 1; }
      n=$((n+1))
    done
    ;;
  cat) [[ $# == 1 ]] || exit 2; file "$1"; hdfs dfs -cat "$1" ;;
  stat)
    [[ $# == 1 ]] || exit 2
    hdfs dfs -ls -d "$1"
    ;;
  list)
    [[ $# == 1 ]] || exit 2
    hdfs dfs -test -d "$1"
    hdfs dfs -ls -R "$1" | awk 'substr($1,1,1)=="-"'
    ;;
  create)
    [[ $# == 1 ]] || exit 2
    if hdfs dfs -test -e "$1"; then echo '文件已存在' >&2; exit 1; fi
    hdfs dfs -mkdir -p "$(dirname -- "$1")"
    hdfs dfs -touchz "$1"
    ;;
  delete) [[ $# == 1 ]] || exit 2; file "$1"; hdfs dfs -rm "$1" ;;
  mkdir) [[ $# == 1 ]] || exit 2; hdfs dfs -mkdir -p "$1" ;;
  rmdir) [[ $# == 1 ]] || exit 2; hdfs dfs -rmdir "$1" ;;
  append)
    [[ $# == 3 ]] || exit 2
    dest=$1; prefix=$2; position=$3
    file "$dest"
    [[ -f "$prefix" ]] || exit 1
    if [[ $position == tail ]]; then
      hdfs dfs -appendToFile "$prefix" "$dest"
    elif [[ $position == head ]]; then
      scratch=$(mktemp -d)
      stage="$(dirname -- "$dest")/.prepend-$(basename -- "$scratch")"
      backup="$(dirname -- "$dest")/.backup-$(basename -- "$scratch")"
      trap 'rm -rf -- "$scratch"; hdfs dfs -rm -f "$stage" >/dev/null 2>&1 || true' EXIT
      hdfs dfs -get "$dest" "$scratch/original"
      cat -- "$prefix" "$scratch/original" > "$scratch/combined"
      hdfs dfs -put "$scratch/combined" "$stage"
      hdfs dfs -chmod "$(hdfs dfs -stat '%a' "$dest")" "$stage"
      hdfs dfs -mv "$dest" "$backup"
      if ! hdfs dfs -mv "$stage" "$dest"; then
        hdfs dfs -mv "$backup" "$dest" || echo "原文件保存在 $backup" >&2
        exit 1
      fi
      hdfs dfs -rm "$backup"
    else exit 2; fi
    ;;
  move)
    [[ $# == 2 ]] || exit 2
    file "$1"
    if hdfs dfs -test -e "$2"; then echo '目标已存在' >&2; exit 1; fi
    hdfs dfs -mkdir -p "$(dirname -- "$2")"
    hdfs dfs -mv "$1" "$2"
    ;;
  *) echo '命令: upload download cat stat list create delete mkdir rmdir append move' >&2; exit 2 ;;
esac
