package org.restct;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.layout.PatternLayout;

import org.restct.dto.Operation;
import org.restct.dto.param.Example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;

import org.restct.utils.Helper;
import org.restct.utils.SendRequest;

public class RESTCT {
    static String outputFolder = "";
    static String dataPath = "";
    static String columnId = "";
    static int SStrength = 0;
    static int EStrength = 0;
    static int AStrength = 0;
    static int budget = 0;

    public static Logger logger;

    static void loadConfig(){
        outputFolder = Config.output_folder;
        dataPath = Config.dataPath;
        budget = Config.budget;
        columnId = Config.columnId;
        SStrength = Config.s_strength;
        EStrength = Config.e_strength;
        AStrength = Config.a_strength;
    }

    static private void loadLogger(){
        String loggerPath = RESTCT.dataPath + "/logs" + "/log_" + LocalDateTime.now() + ".log";

        logger = LogManager.getLogger(RESTCT.class);
//        LoggerContext context = (LoggerContext) LogManager.getContext(false);
//        Configuration config = context.getConfiguration();
//        Configurator.initialize(null , config);


        // Remove all appenders from the root logger
//        config.getRootLogger().removeAppender("console");
//        config.getRootLogger().removeAppender("file");

        // Add a file appender
//        PatternLayout layout = PatternLayout.newBuilder()
//                .withPattern("%highlight{%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n}{FATAL=red blink, ERROR=red, WARN=yellow, INFO=green, DEBUG=blue, TRACE=black}")
//                .build();
//        FileAppender fileAppender = FileAppender.newBuilder()
//                .withFileName(loggerPath)
//                .withAppend(true)
//                .withLayout(layout)
//                .withName("file")
//                .build();
//        fileAppender.start();

        // Add a console appender
//        PatternLayout consoleLayout = PatternLayout.newBuilder()
//                .withPattern("%highlight{%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n}{FATAL=red blink, ERROR=red, WARN=yellow, INFO=green, DEBUG=blue, TRACE=black}")
//                .build();
//
//        ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
//                .withLayout(consoleLayout)
//                .withName("console")
//                .build();


//        Configurator.setLevel(LogManager.getRootLogger().getName(), org.apache.logging.log4j.Level.INFO);
//        consoleAppender.start();
//        config.addAppender(consoleAppender);

//        config.getRootLogger().addAppender(fileAppender, Level.INFO, null);
//        config.getRootLogger().addAppender(consoleAppender, Level.INFO, null);

//        context.updateLoggers(config);
    }

    static void run(){
        long startTime = System.currentTimeMillis();

        loadLogger();
        logger.info("Start running program.");
        try {
            loadConfig();
            //解析OpenAPI，识别参数
            ParseJson.parse();

            logger.info("Operations: {}", Operation.members.size());
            logger.info("Examples found: {}", Example.members.size());
            SeqenceCoveringArray sca = new SeqenceCoveringArray();
            Report.Uncovered = sca.uncoveredSet.size();
            while (!sca.uncoveredSet.isEmpty()) {
                Operation[] sequence = sca.buildSequence();
                logger.info("Uncovered combinations: {}, sequence length: {}", sca.uncoveredSet.size(), sequence.length);

            }

            for (List<Operation> seq : SeqenceCoveringArray.members) {
                CoveringArray ca = new CoveringArray(seq);
                double nowCost = (System.currentTimeMillis() - startTime) / 1000.0;
                logger.debug("budget {} , now cost: {}",budget, nowCost);

                boolean flag = ca.run(RESTCT.budget - nowCost);
                if (!flag) {
                    break;
                }
            }

            Report.Cost = (System.currentTimeMillis() - startTime) / 1000.0;
            Report.report(RESTCT.outputFolder);
        } catch (Exception e) {
            logger.error("Program execution failed: {}", e.getMessage(), e);
        } finally {
            long endTime = System.currentTimeMillis();
            logger.info("Program execution time: {} ms.", endTime - startTime);
        }


    }

}



class Report {
    static int StillUncovered = 0;
    static int Uncovered = 0;
    static double Cost = 0;

    public static double[] getSequenceInfo() {
        /* Get the number of operation sequences exercised (Seq) and their average length (Len) */
        int seq = SeqenceCoveringArray.members.size();
        double length = SeqenceCoveringArray.members.stream().mapToDouble(List::size).average().orElse(0);
        return new double[]{seq, length};
    }

    public static int[] getSequenceTested() {
        /*
        Compute the proportion of 1-way and 2-way sequences that are actually tested
        return: 1-way tested, 1-way all,  2-way tested, 2-way all, still uncovered SStrength-way due to timeout, all uncovered SStrength-way
        */
        int c_1 = Helper.computeCombinations(CoveringArray.successSet, 1);
        int c_1_a = Helper.computeCombinations(SeqenceCoveringArray.members, 1);
        int c_2 = Helper.computeCombinations(CoveringArray.successSet, 2);
        int c_2_a = Helper.computeCombinations(SeqenceCoveringArray.members, 2);
        return new int[]{c_1, c_1_a, c_2, c_2_a, Report.Uncovered};
    }

    public static int getBugInfo() {
        return CoveringArray.bugList.size();
    }

    public static int getRestCallNumber() {
        return SendRequest.getcallNumber();
    }

    public static double getCost() {
        /* return: in minutes */
        return Cost / 60;
    }


    public static void report(String outputFolder) throws IOException {
        double seq = Report.getSequenceInfo()[0];
        double length = Report.getSequenceInfo()[1];
        double c_1 = Report.getSequenceTested()[0];
        double c_1_a = Report.getSequenceTested()[1];
        double c_2 = Report.getSequenceTested()[2];
        double c_2_a = Report.getSequenceTested()[3];
        int a_c = Report.getSequenceTested()[4];
        int bug = Report.getBugInfo();
        int total = Report.getRestCallNumber();
        double cost = Report.getCost();

        RESTCT.logger.debug("SequenceInfo {}\n Sequence Tested {}\n, bug {}\n, total {}\n cost {}\n",Report.getSequenceInfo(),getSequenceTested(),getBugInfo(),getRestCallNumber(),getCost());
        File file = new File(outputFolder + "/statistics.csv");
        String[] columns = {"columnId", "SStrength", "EStrength", "AStrength", "Seq", "Len", "C_1_way", "C_2_way", "All C_SStrength_way", "Bug", "Total", "SuccessHttp", "Cost"};
        if (!file.exists()) {
            try(FileWriter writer = new FileWriter(file))
            {
                writer.write(String.join(",", columns) + "\n");

            } catch (IOException e){

            }
        }
        String data = null;
        try (FileWriter writer = new FileWriter(file.getPath(), true)) {

            data = String.format("%s,%s,%s,%s,%s,%.2f,%.2f,%.2f,%s,%s,%s,%s,%.2f\n",
                    RESTCT.columnId,
                    RESTCT.SStrength,
                    RESTCT.EStrength,
                    RESTCT.AStrength,
                    seq, length,
                    c_1 / c_1_a,
                    c_2 / c_2_a,
                    a_c,
                    bug, total,
                    SendRequest.getSuccessNumber(),
                    cost
                    );
            writer.write(data);
            writer.write("\n");

        } catch (IOException e) {
            System.out.println("写入文件时发生错误：" + e.getMessage());
        }

        RESTCT.logger.info(data);
//        RESTCT.logger.info("{},{},{},{},{},{},{},{},{},{},{},{}\n",
//                RESTCT.columnId, RESTCT.SStrength, RESTCT.EStrength, RESTCT.AStrength, seq, length, c_1 / c_1_a, c_2 / c_2_a, a_c, bug, total, cost);
    }


}