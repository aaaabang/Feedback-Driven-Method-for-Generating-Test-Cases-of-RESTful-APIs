package org.restct;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.restct.dto.*;
import org.restct.dto.keywords.*;
import org.restct.dto.param.AbstractParam;
import org.restct.utils.SendRequest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//import static org.restct.RESTCT.logger;

public class CoveringArray {


    public static Logger logger = LogManager.getLogger(RESTCT.class);

    // value used in success http calls. key: operation._repr_+paramName
    private static Map<String, List<Pair<ValueType, ?>>> okValueDict = new HashMap<>();
    // success http call sequence. key: url, value: last operation's parameters and values
    private static Map<String[], List<Map<String, Pair<ValueType, ?>>>> reuseEssentialSeqDict = new HashMap<>();
    private static Map<String[], List<Map<String, Pair<ValueType, ?>>>> reuseAllSeqDict = new HashMap<>();
    // bug: {url: , method: , parameters: , statusCode: , response: , sequence}
    public static List<Map<String, ?>> bugList = new ArrayList<>();
    // seq actually tested, return 20X or 500
    public static Set<List<String>> successSet = new HashSet<>();

    private List<Operation> sequence;
    private long time;
    private int maxChainItems = 3;
    private List<Map<String, Object>> responseChains = new ArrayList<>();
    private List<Pair<Integer, String>> idCounter = new ArrayList<>();
    private int aStrength;
    private int eStrength;
    private Set<String> unresolvedParam;

    public CoveringArray(List<Operation> sequence) {

        responseChains.add(new HashMap<>());
        // start time
        this.time = System.currentTimeMillis();

        this.sequence = sequence;

        this.aStrength = Config.a_strength; // cover strength for all parameters
        this.eStrength = Config.e_strength; // cover strength for essential parameters

        this.unresolvedParam = new HashSet<>();

        String filePath = Config.dataPath + "/unresolvedParams.json";
        File file = new File(filePath);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("unresolvedParams"))
                    continue;
                this.unresolvedParam.addAll(Arrays.asList(line.split(",")));
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean run(double budget) throws Exception {
        for (int i = 0; i < this.sequence.size(); i++) {
            Operation operation = this.sequence.get(i);
            logger.info("{}-th operation: {}*{}", i + 1, operation.getMethod().getValue(), operation.getUrl());
            List<Map<String, Object>> chainList = this.getChains();
            String[] urlTuple = this.sequence.subList(0, i + 1).stream().map(Operation::toString).toArray(String[]::new);

            while (!chainList.isEmpty()) {
                double nowCost = (System.currentTimeMillis() - this.time)/ 1000.0;
                if (nowCost > budget) {
                    return false;
                }
                Map<String, Object> chain = chainList.remove(0);
                List<String> successUrlTuple = this.sequence.subList(0, i).stream().map(Operation::toString).filter(op -> chain.containsKey(op.toString())).collect(Collectors.toList());
                successUrlTuple.add(operation.toString());

                // solve constraints
                //Processor processor = new Processor(operation.getParameterList());
                List<Constraint> constraints = new ArrayList<>();//processor.parse();
                operation.getConstraints().clear();
                operation.addConstraints(constraints);

                List<Map<String, Pair<ValueType, ?>>> coverArray = this.genEssentialParamsCase(operation, urlTuple, chain);
                //随便救一下
                if(coverArray.isEmpty()){
                    coverArray.add(new HashMap<>());
                }
                logger.info("        {}-th operation essential parameters covering array size: {}, parameters: {}, constraints: {}", i + 1, coverArray.size(), coverArray.get(0).size(), operation.getConstraints().size());
                
                SendRequest essentialSender = new SendRequest(operation, coverArray, chain);
                Pair<List<Integer>, List<Object>> responsePair = essentialSender.run();
                List<Integer> statusCodes = responsePair.getKey();
                List<Object> responses = responsePair.getValue();
                this._handleFeedback(chain, operation, statusCodes, responses, coverArray, successUrlTuple, false);
                Set<Integer> eSuccessCodes = statusCodes.stream().filter(c -> c >= 200 && c < 300).collect(Collectors.toSet());
                Set<Integer> bugCodes = statusCodes.stream().filter(c -> c >= 500 && c < 600).collect(Collectors.toSet());
                if (!eSuccessCodes.isEmpty()) {
                    saveSuccessSeq(successUrlTuple);
                } else if (!bugCodes.isEmpty()) {
                    saveSuccessSeq(successUrlTuple);
                } else {
                    // pass
                }

                coverArray = this.genAllParamsCase(operation, urlTuple, chain);
                //随便救一下
                if(coverArray.isEmpty()){
                    coverArray.add(new HashMap<>());
                }
                logger.info("        {}-th operation all parameters covering array size: {}, parameters: {}", i + 1, coverArray.size(), coverArray.get(0).size());
                logger.info(StringUtils.repeat("*", 100));
                SendRequest allSender = new SendRequest(operation, coverArray, chain);
                responsePair = allSender.run();
                statusCodes = responsePair.getKey();
                responses = responsePair.getValue();
                this._handleFeedback(chain, operation, statusCodes, responses, coverArray, successUrlTuple, true);

                Set<Integer> successCodes = statusCodes.stream().filter(c -> c >= 200 && c < 300).collect(Collectors.toSet());
                bugCodes = statusCodes.stream().filter(c -> c >= 500 && c < 600).collect(Collectors.toSet());
                if (!successCodes.isEmpty()) {
                    this.saveSuccessSeq(successUrlTuple);
                    break;
                } else if (!bugCodes.isEmpty()) {
                    this.saveSuccessSeq(successUrlTuple);
                    break;
                } else {
                    //TODO: 记录unresolved parameters
                }
            }
        }
        logger.info("   unresolved parameters:", this.unresolvedParam);
        this.clearUp();
        return true;
    }

    private List<Map<String, Pair<ValueType,?>>> genEssentialParamsCase(Operation operation, String[] urlTuple, Map<String, Object> chain) throws IOException, InterruptedException {
        Set<AbstractParam> essentialParamList = operation.getParameterList().stream()
                .filter(AbstractParam::isEssential).collect(Collectors.toSet());

        if (essentialParamList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Pair<ValueType, ?>>> reuseSeq = new ArrayList<>();
        for (Map.Entry<String[], List<Map<String, Pair<ValueType, ?>>>> entry: CoveringArray.reuseEssentialSeqDict.entrySet()){
            String[] tmpUrl = entry.getKey();
            if (Arrays.equals(tmpUrl, urlTuple)){
                reuseSeq = entry.getValue();
                break;
            }
        }

        if (!reuseSeq.isEmpty()) {
            // Already executed
            logger.debug("        use reuseSeq info: {}, parameters: {}", reuseSeq.size(), reuseSeq.get(0).size());
            return reuseSeq;
        } else {
            List<AbstractParam> paramList = new ArrayList<>();
            for (AbstractParam p : essentialParamList) {
                paramList.addAll(p.genDomain(operation.toString(), chain, CoveringArray.okValueDict));
            }

            List<String> paramNames = new ArrayList<>();
            List<List<Pair<ValueType, Object>>> domains = new ArrayList<>();

            for(AbstractParam p: paramList) {
                String name = p.getName();
                List<Pair<ValueType, Object>> domain = p.getDomain();

                if(name == "" || paramNames.contains(name) || domain.isEmpty()){
                    continue;
                }
                paramNames.add(name);
                domains.add(domain);
            }

            logger.info("        generate new domains...");
            for (int i = 0; i < paramNames.size(); i++) {
                String name = paramNames.get(i);
                List<Pair<ValueType, Object>> domain = domains.get(i);
                logger.info("            {}: {} - {}", name, domain.size(), domain);
            }

            ACTS acts;
            try {
                acts = new ACTS(paramNames.toArray(new String[0]), domains, operation.getConstraints(), this.eStrength);
            } catch (Exception e) {
                return new ArrayList<>();
            }

            return acts.run();
        }
    }

    private List<Map<String, Pair<ValueType, ?>>> genAllParamsCase(Operation operation, String[] urlTuple, Map<String, Object> chain) {
        Set<AbstractParam> allParamList = operation.getParameterList();
        if (allParamList.size() == 0) {
            return Collections.singletonList(new HashMap<>());
        }

        List<Map<String, Pair<ValueType, ?>>> reuseSeq = new ArrayList<>();
        for (Map.Entry<String[], List<Map<String, Pair<ValueType, ?>>>> entry: CoveringArray.reuseAllSeqDict.entrySet()){
            String[] tmpUrl = entry.getKey();
            if (Arrays.equals(tmpUrl, urlTuple)){
                reuseSeq = entry.getValue();
                break;
            }
        }

        if (reuseSeq.size() > 0) {
            // 执行过
            logger.debug("        use reuseSeq info: {}, parameters: {}", reuseSeq.size(), reuseSeq.get(0).size());
            return reuseSeq;
        } else {
            List<AbstractParam> paramList = new ArrayList<>();
            for (AbstractParam p : allParamList) {
                paramList.addAll(p.genDomain(operation.toString(), chain, CoveringArray.okValueDict));
            }

            List<String> paramNames = new ArrayList<>();
            List<List<Pair<ValueType, Object>>> domains = new ArrayList<>();

            for(AbstractParam p: paramList) {
                String name = p.getName();
                List<Pair<ValueType, Object>> domain = p.getDomain();

                if(name == "" || paramNames.contains(name) || domain.isEmpty()){
                    continue;
                }
                paramNames.add(name);
                domains.add(domain);
            }

            List<Map<String, Pair<ValueType, ? extends Object>>> successEssentialCases = CoveringArray.reuseEssentialSeqDict.getOrDefault(urlTuple, new ArrayList<>());
            if (successEssentialCases.size() > 0) {
                List<String> newParamNames = new ArrayList<>();
                List<List<Pair<ValueType, Object>>> newDomains = new ArrayList<>();
                newParamNames.add("successEssentialCases");
                List<Pair<ValueType, Object>> successEssentialCaseList = new ArrayList<>();
                for (int i = 0; i < successEssentialCases.size(); i++) {
                    successEssentialCaseList.add(new Pair<>(ValueType.NULL, i));
                }
                newDomains.add(successEssentialCaseList);

                for (int i = 0; i < paramNames.size(); i++) {
                    String p = paramNames.get(i);
                    if (!successEssentialCases.get(0).containsKey(p)) {
                        newParamNames.add(p);
                        newDomains.add(domains.get(i));
                    }
                }
                paramNames = newParamNames;
                domains = newDomains;

                for (Constraint c : operation.getConstraints()) {
                    for (String p : c.getParamNames()) {
                        if (this.unresolvedParam.contains(p)) {
                            return new ArrayList<>();
                        }
                    }
                }
            }

            logger.debug("        generate new domains...");
            for (int i = 0; i < paramNames.size(); i++) {
                String p = paramNames.get(i);
                List<Pair<ValueType, Object>> domain = domains.get(i);
                Set<Pair<ValueType, ? extends Object>> valueSet = new HashSet<>();
                for (Pair<ValueType, ? extends Object> item : domain) {
                    valueSet.add(item);
                }
                logger.debug("            {}: {} - {}", p, domain.size(), valueSet);
            }

            try {
                ACTS acts = new ACTS(paramNames.toArray(new String[0]), domains, operation.getConstraints(), this.aStrength);
                List<Map<String, Pair<ValueType, ? extends Object>>> actsOutput = acts.run();
                for (Map<String, Pair<ValueType, ?>> caCase : actsOutput) {

                    if (caCase.containsKey("successEssentialCases")) {
                        Integer successIndex = (Integer) caCase.remove("successEssentialCases").getValue();
                        caCase.putAll(successEssentialCases.get(successIndex));

                    }
                }
                return actsOutput;
            } catch (Exception e) {
                return Collections.singletonList(new HashMap<>());
            }
        }

    }


    private static void saveSuccessSeq(List<String> successUrlTuple) {
        successSet.add(successUrlTuple);
    }

    private void clearUp() {
        // clean resource created
        for (Pair<Integer, String> counter : idCounter) {
            String url = counter.getValue().replaceAll("/$", "");
            String resourceId = url + "/" + counter.getKey();
            try {
                SendRequest.delete(resourceId);
            } catch (Exception e) {
                continue;
            }
        }

        try {
            File file = new File(Config.dataPath + "/unresolvedParams.json");
            if (!file.exists()) {
                FileWriter writer = new FileWriter(file);
                writer.write("unresolvedParams:" + "\n");
                writer.write(String.join("\n", this.unresolvedParam));
                writer.close();
            }
        } catch (Exception e){
            logger.debug("CA.clearup, file write failed", e);
        }
    }

    private void _handleFeedback(Map<String, Object> chain, Operation operation, List<Integer> statusCodes, List<Object> responses,
                                 List<Map<String, Pair<ValueType, ?>>> coverArray, List<String> urlTuple, boolean isAll) {
        for (int i = 0; i < statusCodes.size(); i++) {
            int sc = statusCodes.get(i);
            if (sc < 300) {
                CoveringArray.saveReuse(coverArray.get(i), urlTuple.toArray(new String[0]), isAll);
                CoveringArray.saveOkValue(operation.toString(), coverArray.get(i));
                this.saveChain(chain, operation.toString(), responses.get(i));
            }
            if (operation.method == Method.POST && sc < 300) {
                this.saveIdCount(operation, responses.get(i));
            }
            if (sc >= 500 && sc < 600) {
                CoveringArray.saveBug(operation.getUrl(), operation.method.getValue(),
                        coverArray.get(i), sc, responses.get(i), chain);
            }
        }
    }

    private void saveIdCount(Operation operation, Object response) {
        if (response instanceof Map<?, ?>) {
            Integer iid = (Integer) ((Map<?, ?>) response).get("id");
            try {
                idCounter.add(new Pair<>(iid, operation.getUrl()));
            } catch (NullPointerException e) {
                // do nothing
            }
        } else if (response instanceof List<?>) {
            for (Object r : (List<?>) response) {
                Integer iid = (Integer) ((Map<?, ?>) r).get("id");
                try {
                    idCounter.add(new Pair<>(iid, operation.getUrl()));
                } catch (NullPointerException e) {
                    // do nothing
                }
            }
        }
    }

    private static void saveBug(String url, String method, Map<String, Pair<ValueType, ?>> parameters,
                                 int statusCode, Object response, Object chain) {
        Set<String> opStrSet = new HashSet<>();
        for (Map<String, ?> d : CoveringArray.bugList) {
            String opStr = d.get("method").toString() + d.get("url").toString() + d.get("statusCode").toString();
            opStrSet.add(opStr);
        }
        String currentOpStr = method + url + statusCode;
        if (opStrSet.contains(currentOpStr)) {
            return;
        }

        Map<String, Object> bugInfo = new HashMap<>();
        bugInfo.put("url", url);
        bugInfo.put("method", method);
        Map<String, Pair<ValueType, Object>> paramsCopy = new HashMap<>();
        for (Map.Entry<String, Pair<ValueType, ?>> entry : parameters.entrySet()) {
            Pair<ValueType, ?> pair = entry.getValue();
            paramsCopy.put(entry.getKey(), new Pair<>(pair.getKey(), pair.getValue()));
        }
        bugInfo.put("parameters", paramsCopy);
        bugInfo.put("statusCode", statusCode);
        bugInfo.put("response", response);
        bugInfo.put("responseChain", chain);
        CoveringArray.bugList.add(bugInfo);

        // save to json
        Config config = new Config();
        Path folder = Paths.get(Config.dataPath, "bug");
        if (!Files.exists(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Path bugFile = folder.resolve("bug_" + opStrSet.size() + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(bugFile.toFile())) {
            gson.toJson(bugInfo, writer);
            logger.info("bugFile written successfully.\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveReuse(Map<String, Pair<ValueType, ?>> caseObj, String[] urlTuple, boolean isAll) {
        Map<String[], List<Map<String, Pair<ValueType, ?>>>> toDict;
        if (isAll) {
            toDict = CoveringArray.reuseAllSeqDict;
        } else {
            toDict = CoveringArray.reuseEssentialSeqDict;
        }
        List<Map<String, Pair<ValueType, ?>>> cases = toDict.getOrDefault(urlTuple, new ArrayList<>());
        if (cases.size() < 10) {
            cases.add(caseObj);
            toDict.put(urlTuple, cases);
        }

        if (isAll) {
            CoveringArray.reuseAllSeqDict = toDict;
        } else {
            CoveringArray.reuseEssentialSeqDict = toDict;
        }
    }

    private static void saveOkValue(String opStr, Map<String, Pair<ValueType, ?>> caseObj) {
        for (Map.Entry<String, Pair<ValueType, ?>> params: caseObj.entrySet()) {
            String paramId = opStr + params.getKey();
            if (!CoveringArray.okValueDict.containsKey(paramId)) {
                List<Pair<ValueType, ?>> lst = new ArrayList<>();
                lst.add(params.getValue());
                CoveringArray.okValueDict.put(paramId, lst);
            } else {
                //TODO: do nothing 源码也没做任何事情

                List<Pair<ValueType, ?>> lst = CoveringArray.okValueDict.getOrDefault(paramId,new ArrayList<>());
                List<Object> valueList = lst.stream().map(Pair::getValue).collect(Collectors.toList());
                if (lst.size() < 10 && !valueList.contains(params.getValue().getValue())) {
                    lst.add(params.getValue());
                } else {
                    Set<ValueType> typeSet = lst.stream().map(Pair::getKey).collect(Collectors.toSet());
                    if (!typeSet.contains(params.getValue().getKey())) {
                        lst.add(params.getValue());
                    }
                }

                CoveringArray.okValueDict.put(paramId, lst);
            }
        }
    }

    private void saveChain(Map<String, Object> chain, String opStr, Object response) {
        Map<String, Object> newChain = new HashMap<>(chain);
        newChain.put(opStr, response);
        this.responseChains.add(newChain);
        if (this.responseChains.size() > 10) {
            this.responseChains.remove(0);
        }
    }

    private List<Map<String, Object>> getChains(){
        List<Map<String, Object>> sortedList = new ArrayList<>(this.responseChains);
        sortedList.sort((c1, c2) -> c2.size() - c1.size()); // sort by descending order of chain length
        return sortedList.subList(0, Math.min(this.maxChainItems, sortedList.size())); // return top _maxChainItems chains
    }
}

class ACTS {
    private String[] paramNames;
    List<List<Pair<ValueType, Object>>> domains;
    private List<Constraint> constraints;
    private int strength;
    private Path workplace;
    private Map<String, List<Pair<ValueType, Object>>> valueDict;

    public ACTS(String[] paramNames, List<List<Pair<ValueType, Object>>> domains, List<Constraint> constraints, int strength) throws Exception {
        this.workplace = Paths.get(Config.dataPath, "acts");
        try {
            Files.createDirectories(workplace);
        } catch (IOException e) {
            throw new Exception("failed to create acts folder");
        }

        this.paramNames = paramNames;
        this.domains = domains;
        this.constraints = constraints;
        assert paramNames.length == domains.size();
        valueDict = new HashMap<String, List<Pair<ValueType, Object>>>();
        for (int i = 0; i < paramNames.length; i++) {
            valueDict.put(paramNames[i], domains.get(i));
        }
        this.strength = Math.min(strength, paramNames.length);
        assert this.strength <= paramNames.length;
    }

    public String getId(String paramName) {
        List<String> list = Arrays.asList(paramNames);
        int index = list.indexOf(paramName);

        if (index == -1) {
            throw new IllegalArgumentException("Invalid parameter name");
        }
        return "P" + index;
    }

    public String getName(String paramId) {
        int index = Integer.parseInt(paramId.substring(1));
        return paramNames[index];
    }

    private String transformConstraint(Constraint constraint) {
        String cStr = constraint.toActs(this.valueDict);
        if (cStr == null) {
            return "";
        }
        for (String paramName : constraint.getParamNames()) {
            Pattern pattern = Pattern.compile("\\b" + paramName + "\\b");
            String paramId = this.getId(paramName);
            Matcher matcher = pattern.matcher(cStr);
            cStr = matcher.replaceAll(paramId);
        }
        return cStr;
    }

    public Path writeInput() throws IOException {
        Path inputFile = Paths.get(this.workplace.toString(), "input.txt");
//        CoveringArray.logger.debug("acts inputfile: {}".format(inputFile.toString()));

        FileWriter writer = new FileWriter(inputFile.toFile());

        writer.write("[System]\n");
        writer.write("-- specify system name\n");
        writer.write("Name: acts" + this.strength + "\n\n");
        writer.write("[Parameter]\n");
        writer.write("-- general syntax is parameter_name(type): value1, value2...\n");

        // write parameter ids
        for (String paramName : this.paramNames) {
            List<Pair<ValueType, Object>> domain = this.valueDict.get(paramName);
            writer.write(getId(paramName) + "(int):");
            for (int i = 0; i < domain.size(); i++) {
                writer.write(i + ",");
            }
            writer.write("\n");
        }
        writer.write("\n");

        // write constraints
//        if (this.constraints.size() > 0) {
//            writer.write("[Constraint]\n");
//            for (Constraint c : this.constraints) {
//                String cStr = transformConstraint(c);
//                if (!cStr.isEmpty()) {
//                    writer.write(cStr + "\n");
//                }
//            }
//        }

        writer.close();
        return inputFile;
    }


    private Path callActs(Path inputFile) throws IOException {
        Path outputFile = this.workplace.resolve("output.txt");
//        CoveringArray.logger.debug("acts outputfile: {}".format(outputFile.toString()));

        String algorithm = "ipog";
        String doi = Integer.toString(this.strength);
        String inputFilePath = inputFile.toAbsolutePath().toString();
        String outputFilePath = outputFile.toAbsolutePath().toString();
        String jarPath = Config.jar;

        // acts 的文件路径不可以以"\"作为分割符，会被直接忽略，"\\"需要加上repr，使得"\\"仍然是"\\".
        String[] command = new String[]{"java", "-Dalgo=" + algorithm, "-Ddoi=" + doi, "-Doutput=csv",
                "-jar", jarPath, inputFilePath, outputFilePath};
        ProcessBuilder pb = new ProcessBuilder(command);
        //合并标准输出和标准错误流
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Read the output of the process
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
//        }

        // Wait for the process to complete and get the exit code
        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        CoveringArray.logger.debug("ACTS Exited with code {}", exitCode);
        return outputFile;
    }

    private List<Map<String, Pair<ValueType, ?>>> parseOutput(Path outputFile) {
        List<Map<String, Pair<ValueType, ?>>> coverArray = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(outputFile.toFile()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] paramNames = Arrays.stream(line.trim().split(","))
                        .map(paramId -> this.getName(paramId))
                        .collect(Collectors.toList()).toArray(new String[0]); // Skip the first line with the parameter names

                while ((line = reader.readLine()) != null) {
                    String[] valueIndexList = line.split(",");
                    Map<String, Pair<ValueType, ?>> valueDict = new HashMap<>();

                    for (int i = 0; i < valueIndexList.length; i++) {
                        String paramName = paramNames[i];
                        int valueIndex = Integer.parseInt(valueIndexList[i]);
                        List<Pair<ValueType, Object>> domain = this.valueDict.get(paramName);
                        Object valueTuple = domain.get(valueIndex);

                        valueDict.put(paramName, (Pair<ValueType, ?>) valueTuple);
                    }
                    coverArray.add(valueDict);
                }


            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return coverArray;
    }

    public List<Map<String, Pair<ValueType, ?>>> run() throws IOException, InterruptedException {
        Path inputFile = this.writeInput();
        Path outputFile = this.callActs(inputFile);
        return this.parseOutput(outputFile);
    }
}