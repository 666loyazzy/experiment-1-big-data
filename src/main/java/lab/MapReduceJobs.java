package lab;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class MapReduceJobs {
    public static class DedupMapper extends Mapper<LongWritable, Text, Text, NullWritable> {
        public void map(LongWritable offset, Text line, Context ctx) throws IOException, InterruptedException {
            ctx.write(line, NullWritable.get());
        }
    }

    public static class DedupReducer extends Reducer<Text, NullWritable, Text, NullWritable> {
        public void reduce(Text line, Iterable<NullWritable> values, Context ctx)
                throws IOException, InterruptedException {
            ctx.write(line, NullWritable.get());
        }
    }

    public static class SortMapper extends Mapper<LongWritable, Text, LongWritable, NullWritable> {
        public void map(LongWritable offset, Text line, Context ctx) throws IOException, InterruptedException {
            try {
                ctx.write(new LongWritable(Long.parseLong(line.toString().trim())), NullWritable.get());
            } catch (NumberFormatException e) {
                throw new IOException("输入不是64位整数: " + line, e);
            }
        }
    }

    public static class SortReducer extends Reducer<LongWritable, NullWritable, LongWritable, LongWritable> {
        private long rank = 0;
        public void reduce(LongWritable value, Iterable<NullWritable> occurrences, Context ctx)
                throws IOException, InterruptedException {
            for (NullWritable ignored : occurrences) {
                ctx.write(new LongWritable(++rank), value);
            }
        }
    }

    public static class FamilyMapper extends Mapper<LongWritable, Text, Text, Text> {
        public void map(LongWritable offset, Text line, Context ctx) throws IOException, InterruptedException {
            String[] pair = line.toString().trim().split("\\s+");
            if (pair.length != 2) throw new IOException("关系记录必须有两列: " + line);
            if (pair[0].equals("child") && pair[1].equals("parent")) return;
            
            ctx.write(new Text(pair[0]), new Text("P\t" + pair[1]));
            ctx.write(new Text(pair[1]), new Text("C\t" + pair[0]));
        }
    }

    public static class FamilyReducer extends Reducer<Text, Text, Text, Text> {
        protected void setup(Context ctx) throws IOException, InterruptedException {
            ctx.write(new Text("grandchild"), new Text("grandparent"));
        }
        public void reduce(Text middle, Iterable<Text> links, Context ctx)
                throws IOException, InterruptedException {
            Set<String> children = new TreeSet<>();
            Set<String> parents = new TreeSet<>();
            for (Text link : links) {
                String s = link.toString();
                (s.charAt(0) == 'C' ? children : parents).add(s.substring(2));
            }
            for (String child : children) {
                for (String parent : parents) ctx.write(new Text(child), new Text(parent));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] a = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (a.length < 3) throw new IllegalArgumentException("dedup|sort|family 输入路径 [输入路径...] 输出目录");
        Job job = Job.getInstance(conf, "lab1-" + a[0]);
        job.setJarByClass(MapReduceJobs.class);
        job.setNumReduceTasks(1); 
        switch (a[0]) {
            case "dedup":
                job.setMapperClass(DedupMapper.class);
                job.setCombinerClass(DedupReducer.class);
                job.setReducerClass(DedupReducer.class);
                job.setMapOutputKeyClass(Text.class);
                job.setMapOutputValueClass(NullWritable.class);
                job.setOutputKeyClass(Text.class);
                job.setOutputValueClass(NullWritable.class);
                break;
            case "sort":
                job.setMapperClass(SortMapper.class);
                job.setReducerClass(SortReducer.class);
                job.setMapOutputKeyClass(LongWritable.class);
                job.setMapOutputValueClass(NullWritable.class);
                job.setOutputKeyClass(LongWritable.class);
                job.setOutputValueClass(LongWritable.class);
                break;
            case "family":
                job.setMapperClass(FamilyMapper.class);
                job.setReducerClass(FamilyReducer.class);
                job.setMapOutputKeyClass(Text.class);
                job.setMapOutputValueClass(Text.class);
                job.setOutputKeyClass(Text.class);
                job.setOutputValueClass(Text.class);
                break;
            default:
                throw new IllegalArgumentException("未知任务: " + a[0]);
        }
        for (int i = 1; i < a.length - 1; i++) FileInputFormat.addInputPath(job, new Path(a[i]));
        FileOutputFormat.setOutputPath(job, new Path(a[a.length - 1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
