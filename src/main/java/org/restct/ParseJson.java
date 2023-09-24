package org.restct;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.restct.dto.Operation;
import org.restct.dto.Response;
import org.restct.dto.keywords.DataType;
import org.restct.dto.keywords.DocKey;
import org.restct.dto.keywords.Method;
import org.restct.dto.keywords.ParamKey;
import org.restct.dto.param.Example;
import org.restct.exception.UnsupportedError;
import org.restct.utils.ObjectTypeAdapterRewrite;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.restct.dto.param.Parameter.buildParam;

public class ParseJson {

    private static final Logger logger = LogManager.getLogger(RESTCT.class);

    static String URL_PREFIX = "";
    private static Map<String, Map<String, Object>> DEFINITIONS = new HashMap<>();

    static void parse() throws UnsupportedError, IOException {
        //1. parse definition for examples, definitions dto
        //2. parse paths for operations dto
        //2.1 get parameters dto
        //2.2 get responses dto
        //2.3 get examples dto


        String json = new String(Files.readAllBytes(Paths.get(Config.swagger)), StandardCharsets.UTF_8);
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        ObjectTypeAdapterRewrite objectTypeAdapterRewrite = new ObjectTypeAdapterRewrite();
        Gson gson = new GsonBuilder().registerTypeAdapter(type, objectTypeAdapterRewrite).create();
        Map<String, Object> spec = gson.fromJson(json,type);


        // get url prefix for all resources' url in paths
        URL_PREFIX = _compileUrl(spec);

        // get definitions
        DEFINITIONS = (Map<String, Map<String, Object>>) spec.getOrDefault(DocKey.DEFINITIONS,new HashMap<>());

//        logger.debug("ParseJson URL_PREFIX: {}", URL_PREFIX );
//        logger.debug("ParseJson DEFINITIONS: {}", DEFINITIONS );

        // parse paths
        if (spec.get(DocKey.PATHS) != null){
            Map<String, Object> paths = (Map<String, Object>) spec.get(DocKey.PATHS);
            _parsePaths(paths);
        }

        // parse definitions
        _parseDefinitionExample();


    }



    private static String _compileUrl(Map<String, Object> spec) {
        String protocol = ((List<String>) spec.getOrDefault(DocKey.SCHEMES, Arrays.asList("http"))).get(0);
        String baseurl = (String) spec.getOrDefault(DocKey.BASEPATH, "");
        String host = (String) spec.getOrDefault(DocKey.HOST, "");

        return protocol + "://" + host.trim() + baseurl.trim();
    }



    private static void _parsePaths(Map<String, Object> paths) throws UnsupportedError {
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String urlStr = pathEntry.getKey();
            Map<String, Object> urlInfo = (Map<String, Object>) pathEntry.getValue();
            List<Map<String, Object>> extraParamList = (List<Map<String, Object>>) urlInfo.getOrDefault(DocKey.PARAMS, new ArrayList() {
            });

            for (Map.Entry<String, Object> methodEntry : urlInfo.entrySet()) {
                String methodName = methodEntry.getKey();
                String[] methods = Method.getAllValues();
                if (!(Arrays.asList(methods).contains(methodName))) {
                    continue;
                }
                Map<String, Object> methodInfo = (Map<String, Object>) methodEntry.getValue();


                Operation operation = new Operation(URL_PREFIX.replaceFirst("/$", "") + "/" + urlStr.replaceFirst("^/", ""), methodName);

                // process parameters
                List<Map<String, Object>> paramList = (List<Map<String, Object>>) methodInfo.getOrDefault(DocKey.PARAMS, new ArrayList<>());
                paramList.addAll(extraParamList);
                for (Map<String, Object> paramInfo : paramList) {
                    operation.addParam(buildParam(paramInfo, DEFINITIONS, null));
                    if (paramInfo.containsKey(DocKey.EXAMPLE)) {
                        Example example = new Example((String) paramInfo.get(ParamKey.NAME), paramInfo.get(DocKey.EXAMPLE));
                        example.setOperation(operation);
                        Example.members.add(example);
                    }
                }

                // process responses
                Map<String, Object> responsesInfo = (Map<String, Object>) methodInfo.getOrDefault(DocKey.RESPONSES, new HashMap<>());
                for (Map.Entry<String, Object> responseEntry : responsesInfo.entrySet()) {
                    String statusCode = responseEntry.getKey();
                    Map<String, Object> responseInfo = (Map<String, Object>) responseEntry.getValue();
                    operation.addResponse(Response.buildResponse(statusCode, responseInfo, DEFINITIONS, operation));
                }
            }
        }
    }


    private static void _parseDefinitionExample() throws UnsupportedError {
        for (Map<String, Object> defInfo : DEFINITIONS.values()) {
            Object wholeExamples = defInfo.get(DocKey.EXAMPLE);
            if (wholeExamples != null) {
                if (wholeExamples instanceof List) {
                    for (Object e : (List) wholeExamples) {
                        _parseWholeExample((Map<String, Object>) e);
                    }
                } else if (wholeExamples instanceof Map) {
                    _parseWholeExample((Map<String, Object>) wholeExamples);
                } else {
                    throw new UnsupportedError(wholeExamples + " can not be transferred to example");
                }
            }
            if (DataType.fromValue(String.valueOf(defInfo.getOrDefault(ParamKey.TYPE, DataType.NULL.getValue()))) == DataType.Object) {
                Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) defInfo.get(DocKey.PROPERTIES);
                for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
                    Object singleExample = entry.getValue().get(DocKey.EXAMPLE);
                    if (singleExample != null) {
                        Example example = new Example(entry.getKey(), singleExample);
                        Example.members.add(example);
                    }
                }
            }
        }
    }

    private static void _parseWholeExample(Map<String, Object> exampleInfo) throws UnsupportedError {
        if (exampleInfo == null || exampleInfo.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : exampleInfo.entrySet()) {
            String p_name = entry.getKey();
            Object p_example = entry.getValue();
            if (p_example instanceof List) {
                List<?> list = (List<?>) p_example;
                if (list.isEmpty()) {
                    continue;
                }
                if (list.get(0) instanceof List) {
                    throw new UnsupportedError(list + " can not be transferred to example");
                } else if (list.get(0) instanceof Map) {
                    for (Object sub_example : list) {
                        _parseWholeExample((Map<String, Object>) sub_example);
                    }
                } else {
                    Example example = new Example(p_name, p_example);
                    Example.members.add(example);
                }
            } else if (p_example instanceof Map) {
                _parseWholeExample((Map<String, Object>) p_example);
            } else {
                Example example = new Example(p_name, p_example);
                Example.members.add(example);
            }
        }
    }

}
