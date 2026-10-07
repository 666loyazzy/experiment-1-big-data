package lab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Scanner;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.util.GenericOptionsParser;

public class HdfsOperations {
    private static void requireFile(FileSystem fs, Path path) throws IOException {
        if (!fs.getFileStatus(path).isFile()) {
            throw new IOException("不是文件: " + path);
        }
    }

    private static void printStatus(FileStatus s) {
        
        System.out.printf("%s\t%d\t%s\t%s%n", s.getPermission(), s.getLen(),
                Instant.ofEpochMilli(s.getModificationTime()), s.getPath());
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        IOUtils.copyBytes(in, out, 65536, false);
    }

    private static void upload(FileSystem fs, java.nio.file.Path local, Path dest, String mode)
            throws IOException {
        if (!mode.equals("append") && !mode.equals("overwrite")) {
            throw new IllegalArgumentException("上传模式必须为 append 或 overwrite");
        }
        if (fs.exists(dest)) requireFile(fs, dest);
        if (dest.getParent() != null) fs.mkdirs(dest.getParent());
        try (InputStream in = Files.newInputStream(local);
             OutputStream out = mode.equals("append") && fs.exists(dest)
                     ? fs.append(dest) : fs.create(dest, true)) {
            copy(in, out);
        }
    }

    private static void download(FileSystem fs, Path source, java.nio.file.Path dir)
            throws IOException {
        requireFile(fs, source);
        Files.createDirectories(dir);
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 0; ; n++) {
            java.nio.file.Path dest = dir.resolve(n == 0 ? name : stem + "(" + n + ")" + ext);
            try {
                Files.createFile(dest); 
            } catch (FileAlreadyExistsException e) {
                continue;
            }
            try (InputStream in = fs.open(source); OutputStream out = Files.newOutputStream(dest)) {
                copy(in, out);
            } catch (IOException e) {
                Files.deleteIfExists(dest);
                throw e;
            }
            System.out.println(dest.toAbsolutePath());
            return;
        }
    }

    private static void prepend(FileSystem fs, Path dest, java.nio.file.Path local)
            throws IOException {
        requireFile(fs, dest);
        FileStatus old = fs.getFileStatus(dest);
        Path temp = new Path(dest.getParent(), ".prepend-" + UUID.randomUUID());
        Path backup = new Path(dest.getParent(), ".backup-" + UUID.randomUUID());
        try {
            try (InputStream prefix = Files.newInputStream(local);
                 InputStream original = fs.open(dest);
                 OutputStream out = fs.create(temp, old.getPermission(), false, 65536,
                         old.getReplication(), old.getBlockSize(), null)) {
                copy(prefix, out);
                copy(original, out);
            }
            fs.setPermission(temp, old.getPermission());
            if (!fs.rename(dest, backup)) throw new IOException("备份原文件失败");
            if (!fs.rename(temp, dest)) {
                if (!fs.rename(backup, dest)) throw new IOException("恢复失败，原文件保存在 " + backup);
                throw new IOException("替换失败，已恢复原文件");
            }
            if (!fs.delete(backup, false)) throw new IOException("清理备份失败: " + backup);
        } finally {
            fs.delete(temp, false);
        }
    }

    private static void checkCount(String[] a, int min, int max) {
        if (a.length < min || a.length > max) throw new IllegalArgumentException("命令参数数量错误");
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] a = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (a.length == 0) {
            throw new IllegalArgumentException("upload 本地文件 HDFS路径 [append|overwrite]; download HDFS文件 本地目录; cat/stat/list/create/delete/mkdir/rmdir HDFS路径; append HDFS文件 本地文件 head|tail; move 源文件 目标文件");
        }
        try (FileSystem fs = FileSystem.get(conf)) {
            switch (a[0]) {
                case "upload":
                    checkCount(a, 3, 4);
                    Path dest = new Path(a[2]);
                    String mode = a.length == 4 ? a[3] : "overwrite";
                    if (a.length == 3 && fs.exists(dest)) {
                        System.out.print("目标已存在，请输入 append 或 overwrite: ");
                        mode = new Scanner(System.in).nextLine().trim();
                    }
                    upload(fs, java.nio.file.Path.of(a[1]), dest, mode);
                    break;
                case "download":
                    checkCount(a, 3, 3);
                    download(fs, new Path(a[1]), java.nio.file.Path.of(a[2]));
                    break;
                case "cat":
                    checkCount(a, 2, 2);
                    requireFile(fs, new Path(a[1]));
                    try (InputStream in = fs.open(new Path(a[1]))) { copy(in, System.out); }
                    break;
                case "stat":
                    checkCount(a, 2, 2);
                    System.out.println("权限\t字节数\t最后修改时间(UTC)\t路径");
                    printStatus(fs.getFileStatus(new Path(a[1])));
                    break;
                case "list":
                    checkCount(a, 2, 2);
                    Path dir = new Path(a[1]);
                    if (!fs.getFileStatus(dir).isDirectory()) throw new IOException("不是目录: " + dir);
                    System.out.println("权限\t字节数\t最后修改时间(UTC)\t路径");
                    RemoteIterator<LocatedFileStatus> it = fs.listFiles(dir, true);
                    while (it.hasNext()) printStatus(it.next());
                    break;
                case "create":
                    checkCount(a, 2, 2);
                    Path file = new Path(a[1]);
                    if (file.getParent() != null) fs.mkdirs(file.getParent());
                    try (OutputStream ignored = fs.create(file, false)) { }
                    break;
                case "delete":
                    checkCount(a, 2, 2);
                    Path victim = new Path(a[1]);
                    requireFile(fs, victim);
                    if (!fs.delete(victim, false)) throw new IOException("删除失败: " + victim);
                    break;
                case "mkdir":
                    checkCount(a, 2, 2);
                    if (!fs.mkdirs(new Path(a[1]))) throw new IOException("创建目录失败");
                    break;
                case "rmdir":
                    checkCount(a, 2, 2);
                    Path empty = new Path(a[1]);
                    if (!fs.getFileStatus(empty).isDirectory()) throw new IOException("不是目录: " + empty);
                    if (fs.listStatus(empty).length != 0) throw new IOException("目录非空，保留原目录");
                    if (!fs.delete(empty, false)) throw new IOException("删除目录失败");
                    break;
                case "append":
                    checkCount(a, 4, 4);
                    if (a[3].equals("head")) prepend(fs, new Path(a[1]), java.nio.file.Path.of(a[2]));
                    else if (a[3].equals("tail")) {
                        requireFile(fs, new Path(a[1]));
                        upload(fs, java.nio.file.Path.of(a[2]), new Path(a[1]), "append");
                    } else throw new IllegalArgumentException("追加位置必须为 head 或 tail");
                    break;
                case "move":
                    checkCount(a, 3, 3);
                    Path from = new Path(a[1]);
                    Path to = new Path(a[2]);
                    requireFile(fs, from);
                    if (fs.exists(to)) throw new IOException("目标已存在: " + to);
                    if (to.getParent() != null) fs.mkdirs(to.getParent());
                    if (!fs.rename(from, to)) throw new IOException("移动失败");
                    break;
                default:
                    throw new IllegalArgumentException("未知命令: " + a[0]);
            }
        }
    }
}
