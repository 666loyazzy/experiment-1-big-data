package lab;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.Scanner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.IOUtils;

public class HdfsOperations {
    static void printStatus(FileStatus status) {
        System.out.println(status.getPermission() + " " + status.getLen() + " "
                + new Date(status.getModificationTime()) + " " + status.getPath());
    }

    public static void main(String[] args) throws Exception {
        FileSystem fs = FileSystem.get(new Configuration());
        switch (args[0]) {
            case "upload": {
                Path dest = new Path(args[2]);
                fs.mkdirs(dest.getParent());
                String mode = "overwrite";
                if (fs.exists(dest)) {
                    System.out.print("请选择 append 或 overwrite: ");
                    mode = new Scanner(System.in).next();
                }
                FileInputStream in = new FileInputStream(args[1]);
                OutputStream out;
                if (mode.equals("append")) {
                    out = fs.append(dest);
                } else {
                    out = fs.create(dest, true);
                }
                IOUtils.copyBytes(in, out, 4096, true);
                break;
            }
            case "download": {
                Path source = new Path(args[1]);
                File dir = new File(args[2]);
                dir.mkdirs();
                File dest = new File(dir, source.getName());
                int number = 1;
                while (dest.exists()) {
                    dest = new File(dir, number + "_" + source.getName());
                    number++;
                }
                fs.copyToLocalFile(source, new Path(dest.getPath()));
                System.out.println(dest.getPath());
                break;
            }
            case "cat": {
                FSDataInputStream in = fs.open(new Path(args[1]));
                IOUtils.copyBytes(in, System.out, 4096, false);
                in.close();
                break;
            }
            case "stat":
                printStatus(fs.getFileStatus(new Path(args[1])));
                break;
            case "list": {
                RemoteIterator<LocatedFileStatus> files = fs.listFiles(new Path(args[1]), true);
                while (files.hasNext()) {
                    printStatus(files.next());
                }
                break;
            }
            case "create": {
                Path file = new Path(args[1]);
                fs.mkdirs(file.getParent());
                fs.create(file, false).close();
                break;
            }
            case "delete": {
                Path file = new Path(args[1]);
                if (fs.exists(file) && fs.getFileStatus(file).isFile()) {
                    boolean deleted = fs.delete(file, false);
                    System.out.println(deleted ? "删除成功" : "删除失败");
                } else {
                    System.out.println("文件不存在或不是普通文件");
                }
                break;
            }
            case "mkdir":
                fs.mkdirs(new Path(args[1]));
                break;
            case "rmdir": {
                Path dir = new Path(args[1]);
                if (fs.getFileStatus(dir).isDirectory() && fs.listStatus(dir).length == 0) {
                    fs.delete(dir, false);
                } else {
                    System.out.println("目录非空或不是目录，未删除");
                }
                break;
            }
            case "append": {
                Path file = new Path(args[1]);
                byte[] content = Files.readAllBytes(java.nio.file.Path.of(args[2]));
                if (args[3].equals("head")) {
                    Path temp = new Path(file.getParent(), file.getName() + ".tmp");
                    OutputStream out = fs.create(temp, true);
                    out.write(content);
                    IOUtils.copyBytes(fs.open(file), out, 4096, true);
                    fs.delete(file, false);
                    fs.rename(temp, file);
                } else {
                    OutputStream out = fs.append(file);
                    out.write(content);
                    out.close();
                }
                break;
            }
            case "move": {
                Path source = new Path(args[1]);
                Path dest = new Path(args[2]);
                if (fs.exists(source) && !fs.exists(dest)) {
                    fs.mkdirs(dest.getParent());
                    fs.rename(source, dest);
                } else {
                    System.out.println("源文件不存在或目标路径已存在");
                }
                break;
            }
        }
        fs.close();
    }
}
