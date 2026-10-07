set -e
mkdir -p /tmp/lab-results
cat /etc/os-release > /tmp/lab-results/environment.txt
java -version 2>> /tmp/lab-results/environment.txt
bash --version | head -n 1 >> /tmp/lab-results/environment.txt
curl -fsSL --retry 2 https://archive.apache.org/dist/hadoop/common/hadoop-3.4.3/hadoop-3.4.3.tar.gz -o /tmp/hadoop.tar.gz
sudo tar -xzf /tmp/hadoop.tar.gz -C /usr/local
sudo mv /usr/local/hadoop-3.4.3 /usr/local/hadoop
sudo useradd -m -s /bin/bash hadoop
echo 'hadoop ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/hadoop-lab >/dev/null
sudo chmod 440 /etc/sudoers.d/hadoop-lab
sudo cp -a "$GITHUB_WORKSPACE" /tmp/lab-code
sudo mkdir -p /tmp/lab-hdfs /tmp/lab-input
sudo chown -R hadoop:hadoop /usr/local/hadoop /tmp/lab-code /tmp/lab-hdfs /tmp/lab-input
sudo chmod 777 /tmp/lab-input
sudo tee /usr/local/hadoop/etc/hadoop/core-site.xml >/dev/null <<'XML'
<configuration><property><name>fs.defaultFS</name><value>hdfs://localhost:9000</value></property></configuration>
XML
sudo tee /usr/local/hadoop/etc/hadoop/hdfs-site.xml >/dev/null <<'XML'
<configuration>
<property><name>dfs.replication</name><value>1</value></property>
<property><name>dfs.namenode.name.dir</name><value>file:///tmp/lab-hdfs/name</value></property>
<property><name>dfs.datanode.data.dir</name><value>file:///tmp/lab-hdfs/data</value></property>
</configuration>
XML
sudo tee /usr/local/hadoop/etc/hadoop/mapred-site.xml >/dev/null <<'XML'
<configuration>
<property><name>mapreduce.framework.name</name><value>yarn</value></property>
<property><name>mapreduce.application.classpath</name><value>/usr/local/hadoop/share/hadoop/mapreduce/*:/usr/local/hadoop/share/hadoop/mapreduce/lib/*</value></property>
<property><name>mapreduce.map.memory.mb</name><value>512</value></property>
<property><name>mapreduce.reduce.memory.mb</name><value>512</value></property>
<property><name>mapreduce.map.java.opts</name><value>-Xmx256m</value></property>
<property><name>mapreduce.reduce.java.opts</name><value>-Xmx256m</value></property>
<property><name>yarn.app.mapreduce.am.resource.mb</name><value>512</value></property>
</configuration>
XML
sudo tee /usr/local/hadoop/etc/hadoop/yarn-site.xml >/dev/null <<'XML'
<configuration>
<property><name>yarn.nodemanager.aux-services</name><value>mapreduce_shuffle</value></property>
<property><name>yarn.nodemanager.resource.memory-mb</name><value>4096</value></property>
<property><name>yarn.scheduler.minimum-allocation-mb</name><value>512</value></property>
<property><name>yarn.nodemanager.pmem-check-enabled</name><value>false</value></property>
<property><name>yarn.nodemanager.vmem-check-enabled</name><value>false</value></property>
<property><name>yarn.nodemanager.env-whitelist</name><value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,HADOOP_YARN_HOME,HADOOP_HOME,PATH,LANG,TZ,HADOOP_MAPRED_HOME</value></property>
</configuration>
XML
sudo -u hadoop env JAVA_HOME="$JAVA_HOME" /usr/local/hadoop/bin/hdfs namenode -format -nonInteractive > /tmp/lab-results/format.log 2>&1
