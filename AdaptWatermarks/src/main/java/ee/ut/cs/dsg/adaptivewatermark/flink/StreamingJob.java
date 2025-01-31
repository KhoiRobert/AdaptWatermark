/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ut.cs.dsg.adaptivewatermark.flink;

import ee.ut.cs.dsg.adaptivewatermark.flink.counters.CounterFunction;
import ee.ut.cs.dsg.adaptivewatermark.flink.events.SimpleEvent;
import ee.ut.cs.dsg.adaptivewatermark.flink.periodicassigners.BoundedOutOfOrderWatermarkGenerator;
import ee.ut.cs.dsg.adaptivewatermark.flink.source.AdaptiveWatermarkGeneratorSource;
import ee.ut.cs.dsg.adaptivewatermark.flink.source.FileSourceWithoutWatermarkGenerator;
import ee.ut.cs.dsg.adaptivewatermark.flink.source.StaticSource;
import ee.ut.cs.dsg.adaptivewatermark.flink.source.YetAnotherSource;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichAggregateFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.RichProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;
import org.apache.flink.util.StringUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.Random;
import java.util.TimeZone;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your appliation into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

    private static class AverageFunction extends RichMapFunction<Tuple2<Integer, Long>, String> {
        private transient ValueState<Tuple2<Long, Long>> countSumState;

        @Override
        public String map(Tuple2<Integer, Long> timeWindowLongTuple2) throws Exception {
            Tuple2<Long, Long> currentCountSum = countSumState.value();
            currentCountSum.f0 += 1;
            currentCountSum.f1 += timeWindowLongTuple2.f1;
            //System.out.println(String.format("Average so far is %f", (currentCountSum.f1.doubleValue())/currentCountSum.f0.longValue()));
            countSumState.update(currentCountSum);
            return String.format("Average so far is %f", ((double) currentCountSum.f1.longValue()) / currentCountSum.f0.longValue());

        }

        @Override
        public void open(Configuration config) {
            ValueStateDescriptor<Tuple2<Long, Long>> descriptor =
                    new ValueStateDescriptor<Tuple2<Long, Long>>(
                            "AverageNoElementsPerWindow",
                            TypeInformation.of(
                                    new TypeHint<Tuple2<Long, Long>>() {
                                    }),
                            Tuple2.of(0L, 0L));

            countSumState = getRuntimeContext().getState(descriptor);
        }
    }

    private static class AverageTemperatureFunction implements AllWindowFunction<SimpleEvent, Tuple3<TimeWindow, Double, Integer>, TimeWindow> {
        @Override
        public void apply(TimeWindow window, Iterable<SimpleEvent> iterable, Collector<Tuple3<TimeWindow, Double, Integer>> collector) throws Exception {
            int count = 0;
            double sum = 0.0;
            for (SimpleEvent e : iterable) {
                count++;
                sum += e.getTemperature();
            }

            //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
            collector.collect(new Tuple3<>(window, Double.valueOf(sum / count), Integer.valueOf(count)));
        }
    }
//	private static class CounterFunction implements AllWindowFunction <SimpleEvent, Tuple2<TimeWindow,Long>, TimeWindow>
//	{
//
//
//		@Override
//		public void apply(TimeWindow window, Iterable<SimpleEvent> iterable, Collector<Tuple2<TimeWindow, Long>> collector) throws Exception {
//			long count = 0L;
//			for (SimpleEvent e : iterable)
//			{
//				count++;
//			}
//
//			//System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
//			collector.collect(new Tuple2<>(window, Long.valueOf(count)));
//		}
//	}

    private static class CounterFunction3 implements AllWindowFunction<Tuple3<Long, String, Double>, Tuple3<String, String, Long>, TimeWindow> {


        @Override
        public void apply(TimeWindow window, Iterable<Tuple3<Long, String, Double>> iterable, Collector<Tuple3<String, String, Long>> collector) throws Exception {
            long count = 0L;
            for (Tuple3<Long, String, Double> e : iterable) {
                count++;
            }
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS");
            sdfDate.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date start, end;
            start = new Date(window.getStart());
            end = new Date(window.getEnd());
            //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
            collector.collect(new Tuple3<>(sdfDate.format(start), sdfDate.format(end), Long.valueOf(count)));
        }
    }

    private static class CounterFunction2 implements AllWindowFunction<SimpleEvent, Tuple2<GlobalWindow, Long>, GlobalWindow> {


        @Override
        public void apply(GlobalWindow window, Iterable<SimpleEvent> iterable, Collector<Tuple2<GlobalWindow, Long>> collector) throws Exception {
            long count = 0L;
            for (SimpleEvent e : iterable) {
                count++;
            }
//			System.out.println(window.toString());
            //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
            collector.collect(new Tuple2<>(window, Long.valueOf(count)));
        }
    }

    private static class CounterFunction22 implements AllWindowFunction<Tuple3<Long, String, Double>, Tuple3<String, String, Long>, GlobalWindow> {


        @Override
        public void apply(GlobalWindow window, Iterable<Tuple3<Long, String, Double>> iterable, Collector<Tuple3<String, String, Long>> collector) throws Exception {
            long count = 0L;
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS");
            sdfDate.setTimeZone(TimeZone.getTimeZone("GMT"));
            Long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;

            for (Tuple3<Long, String, Double> e : iterable) {
                min = Long.min(e.f0, min);
                max = Long.max(e.f0, max);
                count++;
            }
//			System.out.println(window.toString());
            //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
            Date start, end;
            start = new Date(min);
            end = new Date(max);
            //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
            collector.collect(new Tuple3<>(sdfDate.format(start), sdfDate.format(end), Long.valueOf(count)));
        }
    }
    public static long sum = 0;

    public static void main(String[] args) throws Exception {
           bulklyRun("/home/khoi/Data/test/AdaptWatermarks/Data/Data/long_dm_s1u_socket_log.txt");

//        ParameterTool parameters = ParameterTool.fromArgs(args);
//        String inputFile, outputFile;
//        boolean adaptive = parameters.getBoolean("adaptive", true);
////        inputFile = parameters.getRequired("input");
//        inputFile = "/home/khoi/Data/test/Adaptive-Watermarks/Data/Data/dm_s1u_socket_log.txt";
//        outputFile = parameters.get("output", "outputFile" + (adaptive ? "Adaptive" : "Periodic"));
//        long maxAllowedLateness = parameters.getLong("allowedLateness", 100);
//        double oooThreshold = parameters.getDouble("oooThreshold", 1);
//        double sensitivity = parameters.getDouble("sensitivity", 1);
//        double sensitivityChangeRate = parameters.getDouble("sensitivityChangeRate", 1);
//        long windowWidth = parameters.getLong("windowWidth", 1000);
//        long period = parameters.getLong("period", 10L);
//
//        // static tests
//
//
////		jobForArrivalRate();
//        if (adaptive) {
//            jobWithAdaptiveWatermarkGeneratorSource(inputFile, outputFile,
//                    maxAllowedLateness, oooThreshold, sensitivity, sensitivityChangeRate, windowWidth);
//        }
//        else {
//            jobWithPeriodicWatermarkGenerator(inputFile, outputFile, maxAllowedLateness, period, windowWidth, 0);
//        }
    }


    public static void bulklyRun(String inputFile) throws Exception {
        double[] snssss = {1};//,0.1,0.01};
        double[] snsChangeRates = {1, 0.1, 0.01};
        double[] oooThresholds = {1.1, 0.1, 0.01};
        long[] winWidths = {/*60*60*1000,*/ 100, 1000};//{100, 1000};
        long[] periods = {200, 10};
        long[] lateness = {100, 1000};
        String[] inputFiles = {inputFile};

        long suggestedWaiting = 92672393L;
        long windowWidth;
        long maxAllowedLateness = 1000;
        long period = 200;
        for (String f : inputFiles) {
            String oFile = f.substring(0, f.indexOf(".") - 1);
            File file;
            for (double scr : snsChangeRates) {
                double sensitivityChangeRate = scr;
                for (double thrshld : oooThresholds) {
                    double oooThreshold = thrshld;

                    for (long w : winWidths) {
                        windowWidth = w;
                        for (double ss : snssss) {
                            double sensitivity = ss;
                            String oFileAdaptive = oFile + "Adaptive" + "L-" + maxAllowedLateness + "OOO-" + oooThreshold + "S-" + sensitivity + "SCR-" + sensitivityChangeRate
                                    + "W-" + windowWidth + ".txt";
                            file = new File(oFileAdaptive);
                            if (!file.exists() && !file.isDirectory()) {
                                jobWithAdaptiveWatermarkGeneratorSource(f, oFileAdaptive, maxAllowedLateness, oooThreshold, sensitivity, sensitivityChangeRate, windowWidth);
                            }
                        }
                    }

                }
            }
            for (long p : periods) {
                period = p;
                for (long l : lateness) {
                    maxAllowedLateness = l;
                    for (long w : winWidths) {
                        windowWidth = w;

                        String ofilePeriodic = oFile + "Periodic" + "L-" + maxAllowedLateness + "P-" + period + "W-" + windowWidth + ".txt";
                        file = new File(ofilePeriodic);
                        if (!file.exists() && !file.isDirectory()) {
                            jobWithPeriodicWatermarkGenerator(f, ofilePeriodic, maxAllowedLateness
                                    , period, windowWidth, suggestedWaiting);
                        }
                    }
                }
            }
        }
    }

    private static void jobWithAdaptiveWatermarkGeneratorSource(String inputFile, String outputFile,
                                                                long allowedLateness, double oooThreshold, double sensitivity,
                                                                double sensitivityChangeRate, long windowWidth) throws Exception {
        // set up the streaming execution environment

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ExecutionConfig executionConfig = env.getConfig();
        executionConfig.setAutoWatermarkInterval(0);
        executionConfig.enableSysoutLogging();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(1);


        AdaptiveWatermarkGeneratorSource src = new AdaptiveWatermarkGeneratorSource(inputFile
                , allowedLateness, oooThreshold, sensitivity, sensitivityChangeRate);
        DataStream<SimpleEvent> stream = env.addSource(src);

        stream

                .keyBy(new KeySelector<SimpleEvent, String>() {

                    @Override
                    public String getKey(SimpleEvent simpleEvent) throws Exception {
                        return simpleEvent.getKey();
                    }
                })
                .timeWindow(Time.milliseconds(windowWidth))

                .process(new RichProcessWindowFunction<SimpleEvent, Tuple3<TimeWindow, Long, Long>, String, TimeWindow>() {
                             @Override
                             public void process(String s, Context context, Iterable<SimpleEvent> iterable, Collector<Tuple3<TimeWindow, Long, Long>> collector) throws Exception {
                                 long count = 0L;
                                 for (SimpleEvent e : iterable) {
                                     count++;
                                 }
                                 sum+=count;
                                 //System.out.println("Num elements in Window ("+window.getStart()+","+window.getEnd()+") is "+count);
                                 collector.collect(new Tuple3<>(context.window(), Long.valueOf(count), context.currentWatermark() - context.window().getEnd()));
                             }
                         }
                )

                .writeAsText(outputFile, FileSystem.WriteMode.OVERWRITE);

        //System.out.println("Total number of generated watermarks "+src.getNumberOfGeneratedWatermarks());
//
        JobExecutionResult result = env.execute("Flink streaming with adaptive watermark generation");

    }


    private static void jobWithPeriodicWatermarkGenerator(String inputFile, String outputFile, long maxOOO, long period, long windowWidth, long waiting) throws Exception {
        // set up the streaming execution environment

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ExecutionConfig executionConfig = env.getConfig();

        executionConfig.disableSysoutLogging();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(1);

        executionConfig.setAutoWatermarkInterval(period);
        executionConfig.enableSysoutLogging();


        DataStream<SimpleEvent> rawStream = env.addSource(new FileSourceWithoutWatermarkGenerator(inputFile));
        BoundedOutOfOrderWatermarkGenerator tsAssigner = new BoundedOutOfOrderWatermarkGenerator(maxOOO);



        rawStream.assignTimestampsAndWatermarks(tsAssigner).keyBy(new KeySelector<SimpleEvent, String>() {

            @Override
            public String getKey(SimpleEvent simpleEvent) throws Exception {
                return simpleEvent.getKey();
            }
        })
                .timeWindow(Time.milliseconds(windowWidth)).allowedLateness(Time.milliseconds(waiting))
                .process(new RichProcessWindowFunction<SimpleEvent, Tuple3<TimeWindow, Long, Long>, String, TimeWindow>() {
                             @Override
                             public void process(String s, Context context, Iterable<SimpleEvent> iterable, Collector<Tuple3<TimeWindow, Long, Long>> collector) throws Exception {
                                 long count = 0L;
                                 for (SimpleEvent e : iterable) {
                                     count++;
                                 }
                                 sum+=count;

                                 collector.collect(new Tuple3<>(context.window(), Long.valueOf(count), context.currentWatermark() - context.window().getEnd()));
                             }
                         }
                ).writeAsText(outputFile, FileSystem.WriteMode.OVERWRITE);


        env.execute("Flink Streaming Java API Skeleton");


    }


}
