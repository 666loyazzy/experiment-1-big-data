#!/usr/bin/env bash
set -euo pipefail
set -x
for path in /tmp/a /tmp/a1 /tmp/test /usr/bashrc1 /usr/test /usr/test2 /test /test.tar.gz /tmp/hello; do
  [[ ! -e "$path" ]] || { echo "实验路径已存在，请先检查: $path" >&2; exit 1; }
done
cd /usr/local
pwd
cd ..
pwd
cd ~
pwd
ls -a /usr
cd /tmp
mkdir a
ls -a /tmp
mkdir -p a1/a2/a3/a4
rmdir a
rmdir -p a1/a2/a3/a4
ls -a /tmp
sudo cp "$HOME/.bashrc" /usr/bashrc1
mkdir /tmp/test
sudo cp -r /tmp/test /usr/
sudo mv /usr/bashrc1 /usr/test/
sudo mv /usr/test /usr/test2
sudo rm /usr/test2/bashrc1
sudo rm -r /usr/test2
cat "$HOME/.bashrc"
tac "$HOME/.bashrc"
more "$HOME/.bashrc"
head -n 20 "$HOME/.bashrc"
head -n -50 "$HOME/.bashrc"
tail -n 20 "$HOME/.bashrc"
tail -n +51 "$HOME/.bashrc"
touch /tmp/hello
stat /tmp/hello
touch -d '5 days ago' /tmp/hello
stat /tmp/hello
sudo chown root /tmp/hello
ls -l /tmp/hello
find "$HOME" -name .bashrc -type f
sudo mkdir /test
sudo tar -czf /test.tar.gz -C / test
tar -xzf /test.tar.gz -C /tmp
grep 'examples' "$HOME/.bashrc" || [[ $? == 1 ]]
