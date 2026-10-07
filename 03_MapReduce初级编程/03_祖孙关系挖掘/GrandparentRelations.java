package lab;

import java.io.IOException;
import java.util.ArrayList;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class GrandparentRelations {
    public static class FamilyMapper extends Mapper<LongWritable, Text, Text, Text> {
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] pair = value.toString().trim().split("\\s+");
            if (pair[0].equals("child")) {
                return;
            }
            context.write(new Text(pair[1]), new Text("child\t" + pair[0]));
            context.write(new Text(pair[0]), new Text("parent\t" + pair[1]));
        }
    }

    public static class FamilyReducer extends Reducer<Text, Text, Text, Text> {
        protected void setup(Context context) throws IOException, InterruptedException {
            context.write(new Text("grandchild"), new Text("grandparent"));
        }

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            ArrayList<String> children = new ArrayList<>();
            ArrayList<String> parents = new ArrayList<>();
            for (Text value : values) {
                String[] pair = value.toString().split("\t");
                if (pair[0].equals("child")) {
                    children.add(pair[1]);
                } else {
                    parents.add(pair[1]);
                }
            }
            for (String child : children) {
                for (String parent : parents) {
                    context.write(new Text(child), new Text(parent));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Job job = Job.getInstance(new Configuration(), "祖孙关系挖掘");
        job.setJarByClass(GrandparentRelations.class);
        job.setMapperClass(FamilyMapper.class);
        job.setReducerClass(FamilyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(1);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.waitForCompletion(true);
    }
}
