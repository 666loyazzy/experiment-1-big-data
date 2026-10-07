import json
import os
from pathlib import Path
import subprocess
import time

repo = Path('/tmp/lab-code')
result_dir = Path('/tmp/lab-results')
data = Path('/tmp/lab-input')
prefix = ['sudo', '-u', 'hadoop', '-H', 'env', 'JAVA_HOME=' + os.environ['JAVA_HOME'], 'HADOOP_HOME=/usr/local/hadoop', 'HADOOP_COMMON_HOME=/usr/local/hadoop', 'HADOOP_HDFS_HOME=/usr/local/hadoop', 'HADOOP_MAPRED_HOME=/usr/local/hadoop', 'HADOOP_YARN_HOME=/usr/local/hadoop', 'PATH=/usr/local/hadoop/bin:' + os.environ['PATH']]
summary = {}
log = (result_dir / 'commands.log').open('w', encoding='utf-8')

def run(args, input=None, allowed=(0,), timeout=180):
    command = prefix + [str(arg) for arg in args]
    result = subprocess.run(command, input=input, text=True, capture_output=True, timeout=timeout)
    log.write('$ ' + ' '.join(str(arg) for arg in args) + '\n' + result.stdout + result.stderr + '\n')
    log.flush()
    assert result.returncode in allowed, (args, result.returncode, result.stdout[-1000:], result.stderr[-3000:])
    return result.stdout

def dfs(*args, **kwargs):
    return run(['hdfs', 'dfs', *args], **kwargs)

def save():
    (result_dir / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False), flush=True)

for path in repo.rglob('*.sh'):
    run(['bash', '-n', path])
summary['shell_syntax'] = 7
linux = repo / '01_熟悉常用的Linux操作和Hadoop操作' / '01_Linux常用命令.sh'
output = run(['bash', '-e', linux])
(result_dir / 'linux.log').write_text(output, encoding='utf-8')
summary['linux_commands'] = 'passed on Ubuntu 22.04'
save()
basic = repo / '01_熟悉常用的Linux操作和Hadoop操作' / '02_Hadoop基本操作.sh'
run(['bash', '-e', basic], timeout=240)
assert dfs('-cat', '/user/hadoop/test/.bashrc') == Path('/home/hadoop/.bashrc').read_text()
assert Path('/usr/local/hadoop/test/.bashrc').read_bytes() == Path('/home/hadoop/.bashrc').read_bytes()
summary['hadoop_basic'] = 'passed'
save()

for name, content in [('data.txt', 'Hello Hadoop\n'), ('add.txt', 'New line\n'), ('head.txt', 'First line\n')]:
    (data / name).write_text(content)
subprocess.run(['sudo', 'chown', '-R', 'hadoop:hadoop', str(data)], check=True)
for implementation in ['java', 'shell']:
    base = '/user/hadoop/verify-' + implementation
    target = base + '/data.txt'
    if implementation == 'java':
        invoke = ['hadoop', 'jar', repo / 'target/lab1.jar', 'lab.HdfsOperations']
    else:
        invoke = ['bash', repo / '02_熟悉常用的HDFS操作/Shell实现/HDFS操作.sh']
    run(invoke + ['upload', data / 'data.txt', target])
    run(invoke + ['upload', data / 'add.txt', target], input='append\n')
    assert dfs('-cat', target) == 'Hello Hadoop\nNew line\n'
    run(invoke + ['upload', data / 'data.txt', target], input='overwrite\n')
    assert run(invoke + ['cat', target]) == 'Hello Hadoop\n'
    download = data / ('download-' + implementation)
    download.mkdir()
    (download / 'data.txt').write_text('keep this file\n')
    subprocess.run(['sudo', 'chown', '-R', 'hadoop:hadoop', str(download)], check=True)
    run(invoke + ['download', target, download])
    assert (download / 'data.txt').read_text() == 'keep this file\n'
    assert (download / '1_data.txt').read_text() == 'Hello Hadoop\n'
    status = run(invoke + ['stat', target])
    assert 'rw-' in status and '13' in status and target in status
    run(invoke + ['create', base + '/sub/empty.txt'])
    assert dfs('-stat', '%b', base + '/sub/empty.txt').strip() == '0'
    listing = run(invoke + ['list', base])
    assert target in listing and '/sub/empty.txt' in listing
    run(invoke + ['mkdir', base + '/dirs/a/b'])
    run(invoke + ['rmdir', base + '/dirs/a/b'])
    dfs('-test', '-e', base + '/dirs/a/b', allowed=(1,))
    run(invoke + ['rmdir', base + '/sub'], allowed=(0,) if implementation == 'java' else (1,))
    dfs('-test', '-e', base + '/sub/empty.txt')
    run(invoke + ['append', target, data / 'add.txt', 'tail'])
    run(invoke + ['append', target, data / 'head.txt', 'head'])
    assert dfs('-cat', target) == 'First line\nHello Hadoop\nNew line\n'
    moved = base + '/moved/data.txt'
    run(invoke + ['move', target, moved])
    dfs('-test', '-e', target, allowed=(1,))
    assert dfs('-cat', moved) == 'First line\nHello Hadoop\nNew line\n'
    run(invoke + ['delete', moved])
    run(invoke + ['delete', base + '/sub/empty.txt'])
    dfs('-test', '-e', moved, allowed=(1,))
    summary[implementation + '_hdfs'] = 'upload append/overwrite, download rename, cat, stat, recursive list, create/delete, mkdir/rmdir, prepend/append, move passed'
    save()

run(['yarn', '--daemon', 'start', 'resourcemanager'])
run(['yarn', '--daemon', 'start', 'nodemanager'])
for i in range(30):
    status = run(['yarn', 'node', '-list'], allowed=(0, 1), timeout=30)
    if 'RUNNING' in status:
        break
    time.sleep(2)
else:
    raise AssertionError('YARN node did not register')
tasks = repo / '03_MapReduce初级编程'
for name, directory in [('dedup', '01_文件合并与去重'), ('sort', '02_整数排序'), ('family', '03_祖孙关系挖掘')]:
    task = tasks / directory
    dest = '/user/hadoop/mapreduce/' + name
    dfs('-mkdir', '-p', dest + '/input')
    for path in sorted((task / '输入').glob('*.txt')):
        dfs('-put', path, dest + '/input/')
    run(['bash', task / '运行.sh', dest + '/input', dest + '/output'], timeout=240)
    output = dfs('-cat', dest + '/output/part-r-00000')
    (result_dir / (name + '.txt')).write_text(output, encoding='utf-8')
    if name == 'dedup':
        expected = sorted(set(' '.join(line.split()) for p in (task / '输入').glob('*.txt') for line in p.read_text().splitlines()))
        assert output.splitlines() == expected
    elif name == 'sort':
        numbers = sorted(int(line) for p in (task / '输入').glob('*.txt') for line in p.read_text().splitlines())
        assert [tuple(map(int, line.split())) for line in output.splitlines()] == list(enumerate(numbers, 1))
    else:
        pairs = [line.split() for line in (task / '输入/child-parent.txt').read_text().splitlines()[1:]]
        expected = sorted((a, d) for a, b in pairs for c, d in pairs if b == c)
        lines = output.splitlines()
        assert lines[0].split() == ['grandchild', 'grandparent']
        assert sorted(tuple(line.split()) for line in lines[1:]) == expected
    summary['mapreduce_' + name] = 'passed on YARN'
    save()
