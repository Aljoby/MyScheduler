package dmir.tkl.topology.testRAS;

import dmir.tkl.topology.testRAS.RASConfigUtil;
import dmir.tkl.topology.testRAS.RASOutput;
import dmir.tkl.topology.testRAS.RASRandomSentenceSpout;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by 44931 on 2017/12/19.
 */
public class RASwcTopology {
    public static class RASSplitSentence extends BaseRichBolt {

        private static final long serialVersionUID = 9182719848878455933L;
        private OutputCollector collector;

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word"));
        }

        @Override
        public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
            collector = outputCollector;
        }

        @Override
        public void execute(Tuple tuple) {
            String sentence = tuple.getStringByField("sentence");
            String[] sentenceSplit = sentence.split(" ");
            for (int i = 0; i < sentenceSplit.length; i++) {
                collector.emit(tuple, new Values(sentenceSplit[i]));
            }
            collector.ack(tuple);
        }

        @Override
        public void cleanup() {
            System.out.println("Split cleanup");
        }
    }

    public static class RASWordCount extends BaseRichBolt {
        private static final long serialVersionUID = 4905347466083499207L;
        private Map<String, Integer> counters;
        private OutputCollector collector;

        private long getNumWords() {
            //counters.rotate();
            return counters.size();
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word", "count"));
        }

        @Override
        public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
            collector = outputCollector;
            counters = (Map<String, Integer>) topologyContext.getTaskData("words");
            if (counters == null) {
                counters = new HashMap<>();
                topologyContext.setTaskData("words", counters);
            }
        }

        @Override
        public void execute(Tuple tuple) {
            String word = tuple.getStringByField("word");
            Integer count = counters.get(word);
            if (count == null) {
                count = 0;
            }
            count++;
            counters.put(word, count);
            collector.emit(new Values(word, count));
            collector.ack(tuple);
        }

        @Override
        public void cleanup() {
            System.out.println("Word Counter cleanup");
        }

    }

    public static void main(String[] args) throws Exception {
        Config conf = RASConfigUtil.readConfig(new File(args[1]));
        if (conf == null) {
            throw new RuntimeException("cannot find conf file " + args[0]);
        }
        TopologyBuilder builder = new TopologyBuilder();

        //int defaultTaskNum = RASConfigUtil.getInt(conf, "defaultTaskNum", 1);

        int spoutCPU = RASConfigUtil.getInt(conf, "spout.cpu", 50);
        int spoutOnHeap = RASConfigUtil.getInt(conf, "spout.onheap", 100);
        int spoutOffHeap = RASConfigUtil.getInt(conf, "spout.offheap", 100);
        builder.setSpout("say", new RASRandomSentenceSpout(),
                RASConfigUtil.getInt(conf, "spout.parallelism", 1)).setCPULoad(spoutCPU);

        int splitCPU = RASConfigUtil.getInt(conf, "split.cpu", 150);
        int splitOnHeap = RASConfigUtil.getInt(conf, "split.onheap", 100);
        int splitOffHeap = RASConfigUtil.getInt(conf, "split.offheap", 100);
        builder.setBolt("split", new RASSplitSentence(),
                RASConfigUtil.getInt(conf, "split.parallelism", 1))
                //.setNumTasks(defaultTaskNum)
                .shuffleGrouping("say").setCPULoad(splitCPU);

        int countCPU = RASConfigUtil.getInt(conf, "count.cpu", 150);
        int countOnHeap = RASConfigUtil.getInt(conf, "count.onheap", 100);
        int countOffHeap = RASConfigUtil.getInt(conf, "count.offheap", 100);
        builder.setBolt("counter", new RASWordCount(),
                RASConfigUtil.getInt(conf, "counter.parallelism", 1))
                //.setNumTasks(defaultTaskNum)
                .shuffleGrouping("split").setCPULoad(countCPU);
        //.fieldsGrouping("split", new Fields("word"));

        int outputCPU = RASConfigUtil.getInt(conf, "output.cpu", 60);
        int outputOnHeap = RASConfigUtil.getInt(conf, "output.onheap", 100);
        int outputOffHeap = RASConfigUtil.getInt(conf, "output.offheap", 100);
        builder.setBolt("output", new RASOutput(),
                RASConfigUtil.getInt(conf, "output.parallelism", 1))
                .shuffleGrouping("counter").setCPULoad(outputCPU);

        conf.setNumWorkers(RASConfigUtil.getInt(conf, "wc-NumOfWorkers", 2));
        //conf.setMaxSpoutPending(RASConfigUtil.getInt(conf, "wc-MaxSpoutPending", 0));
        conf.setDebug(RASConfigUtil.getBoolean(conf, "DebugTopology", false));
        //conf.setStatsSampleRate(RASConfigUtil.getDouble(conf, "StatsSampleRate", 1.0));
        //conf.registerMetricsConsumer(LoggingMetricsConsumer.class);
        StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
    }
}
