package lab;

import java.io.IOException;
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

public class IntegerSort {
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

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] paths = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (paths.length < 2) throw new IllegalArgumentException("输入路径 [输入路径...] 输出目录");
        Job job = Job.getInstance(conf, "整数排序");
        job.setJarByClass(IntegerSort.class);
        job.setNumReduceTasks(1);
        job.setMapperClass(SortMapper.class);
        job.setReducerClass(SortReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(LongWritable.class);
        for (int i = 0; i < paths.length - 1; i++) {
            FileInputFormat.addInputPath(job, new Path(paths[i]));
        }
        FileOutputFormat.setOutputPath(job, new Path(paths[paths.length - 1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
