package org.restct;

import org.apache.commons.cli.*;
import org.json.JSONException;
import org.json.JSONObject;
import sun.tools.jar.resources.jar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    // swagger file path
     static String swagger = "";

    // operation sequence covering strength
     static Integer s_strength = 2;

    // all parameter covering strength
     static Integer a_strength = 2;

    // essential parameter covering strength
     static Integer e_strength = 3;

    // maximum of op_cover_strength
     static final Integer MAX_OP_COVER_STRENGTH = 5;

    // minimum of op_cover_strength
     static final Integer MIN_OP_COVER_STRENGTH = 1;

    // maximum of param_cover_strength
    static final Integer MAX_PARAM_COVER_STRENGTH = 5;

    // minimum of param_cover_strength
    static final Integer MIN_PARAM_COVER_STRENGTH = 1;

    // output folder
    static String output_folder = "";

    // test budget (secs)
    static Integer budget = 3600;

    // constraint patterns for nlp recognition
    public static String patterns = "";

    // acts jar file
    static String jar = "";

    // auth token
    public static Map<String, Object> header = new HashMap<>();

    // auth token
    public static Map<String, Object> query = new HashMap<>();

    // experiment unique name
    static String columnId = "";

    // data and log path
    public static String dataPath = "";


   static private void checkAndPrehandling(Map<String, String> settings) throws Exception {
        //src/main/java/com/example/Main.java文件的路径
       Path curFile = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "com", "example", "Main.java");

       //TODO：注释掉输出
//       System.out.println("curFile:" + curFile.getParent().toString());

       if (Files.exists(Paths.get(settings.getOrDefault("swagger","")))) {
           Config.swagger = settings.get("swagger");
       } else {
           throw new Exception("swagger json does not exist");
       }

       Integer SStrength = Integer.parseInt(settings.getOrDefault("SStrength", "2"));
       if (Config.MIN_OP_COVER_STRENGTH <= SStrength && SStrength <= Config.MAX_OP_COVER_STRENGTH) {
           Config.s_strength = SStrength;
       } else {
           throw new Exception("operation sequence covering strength must be in [" + Config.MIN_OP_COVER_STRENGTH + ", " + Config.MAX_OP_COVER_STRENGTH + "]");
       }

       int EStrength = Integer.parseInt(settings.getOrDefault("EStrength", "3").toString());
       if (Config.MIN_PARAM_COVER_STRENGTH <= EStrength && EStrength <= Config.MAX_PARAM_COVER_STRENGTH) {
           Config.e_strength = EStrength;
       } else {
           throw new Exception("essential parameter covering strength must be in [" + Config.MIN_PARAM_COVER_STRENGTH + ", " + Config.MAX_PARAM_COVER_STRENGTH + "]");
       }

       int AStrength = Integer.parseInt(settings.getOrDefault("AStrength", "2").toString());
       if (Config.MIN_PARAM_COVER_STRENGTH <= AStrength && AStrength <= Config.MAX_PARAM_COVER_STRENGTH) {
           Config.a_strength = AStrength;
       } else {
           throw new Exception("all parameter covering strength must be in [" + Config.MIN_PARAM_COVER_STRENGTH + ", " + Config.MAX_PARAM_COVER_STRENGTH + "]");
       }

       Path folder = Paths.get(settings.get("dir"));

       Config.output_folder = settings.get("dir");
       try {
           Files.createDirectories(folder);
       } catch (IOException e) {
           throw new Exception("failed to create output folder");
       }

       int budget = Integer.parseInt(settings.getOrDefault("budget", "3600").toString());
       if (budget == 0) {
           throw new Exception("test budget cannot be zero");
       } else {
           Config.budget = budget;
       }

       String workingDir = System.getProperty("user.dir");
       System.out.println("当前工作目录为：" + workingDir);

       String patternsPath = settings.getOrDefault("patterns", workingDir + "/src/main/resources/lib/matchrules.json");
       Path patterns = Paths.get(patternsPath);
       if (Files.exists(patterns) && Files.isRegularFile(patterns)) {
           Config.patterns = patterns.toString();
       } else {
           throw new Exception("patterns are not provided");
       }

       String jarPath = settings.getOrDefault("jar", workingDir + "/src/main/resources/lib/acts_2.93.jar");
       Path jarFile = Paths.get(jarPath);
       if (Files.exists(jarFile) && Files.isRegularFile(jarFile)) {
           Config.jar = jarFile.toString();
       } else {
           throw new Exception("acts jar is not provided");
       }

       try {
           JSONObject authToken = new JSONObject(settings.getOrDefault("header", "{}"));
           Config.header.putAll(authToken.toMap());
       } catch (JSONException e) {
           throw new Exception("expecting strings enclosed in double quotes");
       }
       try {
           JSONObject authToken = new JSONObject(settings.getOrDefault("query", "{}"));
           Config.query.putAll(authToken.toMap());
       } catch (JSONException e) {
           throw new Exception("expecting strings enclosed in double quotes");
       }
       if (settings.get("columnId") == null || settings.get("columnId").equals("")) {
           Config.columnId = new File(settings.get("swagger")).getName().replaceFirst("[.][^.]+$", "");
       } else {
           Config.columnId = settings.get("columnId");
       }

       Path dataPath = folder.resolve(Config.columnId);
       Config.dataPath = dataPath.toString();
       if (!Files.exists(dataPath)) {
           Files.createDirectory(dataPath);
       }

   }

   static void parseArgs(String[] args) throws Exception {
       Options options = new Options();

       // Define the command-line arguments
       options.addOption(Option.builder()
               .longOpt("swagger")
               .desc("abs path of swagger file")
               .hasArg()
               .required()
               .build());

       options.addOption(Option.builder()
               .longOpt("SStrength")
               .desc("operation sequence covering strength")
               .hasArg()
               .type(Integer.class)
               .build());

       options.addOption(Option.builder()
               .longOpt("EStrength")
               .desc("essential parameter covering strength")
               .hasArg()

               .type(Integer.class)
               .build());

       options.addOption(Option.builder()
               .longOpt("AStrength")
               .desc("all parameter covering strength")
               .hasArg()
               .type(Integer.class)
               .build());

       options.addOption(Option.builder()
               .longOpt("dir")
               .desc("output folder")
               .hasArg()
               .required()
               .build());

       options.addOption(Option.builder()
               .longOpt("budget")
               .desc("test budget(Secs), default=3600")
               .hasArg()
               .type(Integer.class)
               .build());

       options.addOption(Option.builder()
               .longOpt("patterns")
               .desc("constraint patterns for nlp processes")
               .hasArg()
               .build());

       options.addOption(Option.builder()
               .longOpt("jar")
               .desc("acts jar file")
               .hasArg()
               .build());

       options.addOption(Option.builder()
               .longOpt("header")
               .desc("auth token: {keyName: token}")
               .hasArg()
               .build());

       options.addOption(Option.builder()
               .longOpt("query")
               .desc("auth token: {keyName: token}")
               .hasArg()
               .build());

       options.addOption(Option.builder()
               .longOpt("columnId")
               .desc("experiment unique name for statistic data")
               .hasArg()
               .build());

       CommandLineParser parser = new DefaultParser();
       Map<String, String> settings = new HashMap<>();
       try {
           CommandLine cmd = parser.parse(options, args);

           for (Option opt : cmd.getOptions()) {
               if(cmd.getOptionValue(opt.getLongOpt()) != null)
                settings.put(opt.getLongOpt(), cmd.getOptionValue(opt.getLongOpt()));
           }
           // 处理命令行参数
//           settings.put("swagger", cmd.getOptionValue("swagger"));
//           settings.put("dir", cmd.getOptionValue("dir"));
//           settings.put("patterns", cmd.getOptionValue("patterns"));
//           settings.put("jar", cmd.getOptionValue("jar"));
//           settings.put("header", cmd.getOptionValue("header"));
//           settings.put("query", cmd.getOptionValue("query"));
//           settings.put("columnId", cmd.getOptionValue("columnId"));
//           settings.put("SStrength", cmd.getParsedOptionValue("SStrength"));
//           settings.put("EStrength", cmd.getParsedOptionValue("EStrength"));
//           settings.put("AStrength", cmd.getParsedOptionValue("AStrength"));
//           settings.put("budget", cmd.getParsedOptionValue("budget"));

       } catch (ParseException e) {
           System.out.println(e.getMessage());
       }


       checkAndPrehandling(settings);
   }


}
