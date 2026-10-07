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

public class MergeDedup {
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

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] paths = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (paths.length < 2) throw new IllegalArgumentException("输入路径 [输入路径...] 输出目录");
        Job job = Job.getInstance(conf, "文件合并与去重");
        job.setJarByClass(MergeDedup.class);
        job.setNumReduceTasks(1);
        job.setMapperClass(DedupMapper.class);
        job.setCombinerClass(DedupReducer.class);
        job.setReducerClass(DedupReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        for (int i = 0; i < paths.length - 1; i++) {
            FileInputFormat.addInputPath(job, new Path(paths[i]));
        }
        FileOutputFormat.setOutputPath(job, new Path(paths[paths.length - 1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
