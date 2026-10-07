package lab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
                IOUtils.copyBytes(fs.open(source), new FileOutputStream(dest), 4096, true);
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
                fs.create(file).close();
                break;
            }
            case "delete":
                fs.delete(new Path(args[1]), false);
                break;
            case "mkdir":
                fs.mkdirs(new Path(args[1]));
                break;
            case "rmdir":
                fs.delete(new Path(args[1]), false);
                break;
            case "append": {
                Path file = new Path(args[1]);
                byte[] content = Files.readAllBytes(java.nio.file.Path.of(args[2]));
                if (args[3].equals("head")) {
                    FSDataInputStream in = fs.open(file);
                    byte[] original = in.readAllBytes();
                    in.close();
                    OutputStream out = fs.create(file, true);
                    out.write(content);
                    out.write(original);
                    out.close();
                } else {
                    OutputStream out = fs.append(file);
                    out.write(content);
                    out.close();
                }
                break;
            }
            case "move": {
                Path dest = new Path(args[2]);
                fs.mkdirs(dest.getParent());
                fs.rename(new Path(args[1]), dest);
                break;
            }
        }
        fs.close();
    }
}
