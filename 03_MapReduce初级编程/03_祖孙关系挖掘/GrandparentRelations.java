package lab;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class GrandparentRelations {
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
        String[] paths = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (paths.length < 2) throw new IllegalArgumentException("输入路径 [输入路径...] 输出目录");
        Job job = Job.getInstance(conf, "祖孙关系挖掘");
        job.setJarByClass(GrandparentRelations.class);
        job.setNumReduceTasks(1);
        job.setMapperClass(FamilyMapper.class);
        job.setReducerClass(FamilyReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        for (int i = 0; i < paths.length - 1; i++) {
            FileInputFormat.addInputPath(job, new Path(paths[i]));
        }
        FileOutputFormat.setOutputPath(job, new Path(paths[paths.length - 1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
