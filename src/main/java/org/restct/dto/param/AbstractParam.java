package org.restct.dto.param;

import javafx.util.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.restct.RESTCT;
import org.restct.dto.keywords.*;
import org.restct.exception.UnsupportedError;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//模糊测试
class Fuzzer {
    private static Random random = new Random();

    @NotNull
    public static String deleteRandomCharacter(String s, String not_used) {
        if (s.isEmpty()) {
            return s;
        }
        int pos = random.nextInt(s.length());
        return s.substring(0, pos) + s.substring(pos + 1);
    }

    @NotNull
    public static String insertRandomCharacter(String s, String t) {
        int pos = random.nextInt(s.length() + 1);
        char randomCharacter;
        if (t.equals("int")) {
            randomCharacter = (char) (random.nextInt(10) + '0');
        } else if (t.equals("string")) {
            randomCharacter = (char) (random.nextInt(26) + 'a');
        } else {
            randomCharacter = (char) (random.nextInt(62) + 'a');
        }
        return s.substring(0, pos) + randomCharacter + s.substring(pos);
    }

    @NotNull
    public static String flipRandomCharacter(String s) {
        if (s.isEmpty()) {
            return s;
        }
        int pos = random.nextInt(s.length());
        char c = s.charAt(pos);
        int bit = 1 << random.nextInt(7);
        char newC = (char) (c ^ bit);
        return s.substring(0, pos) + newC + s.substring(pos + 1);
    }

    @NotNull
    public static String mutateStr(String s, String t) {
        List<BiFunction<String, String, String>> mutators = Arrays.asList(
                Fuzzer::deleteRandomCharacter,
                Fuzzer::insertRandomCharacter
        );
        BiFunction<String, String, String> mutator = mutators.get(new Random().nextInt(mutators.size()));
        return mutator.apply(s, t);
    }

    @NotNull
    public static List<Object> mutate(Object v, int r) {
        List<Object> result = new ArrayList<>();
        if (v instanceof Integer) {
            for (int i = 0; i < r; i++) {
                result.add(random.nextInt(9) + 1);
            }
        } else if (v instanceof Float) {
            for (int i = 0; i < r; i++) {
                result.add((float) Math.round(random.nextFloat() * 100 * 100) / 100);
            }
        } else {
            String strV = v.toString();
            String[] tSet = {"string", "int"};
            for (int i = 0; i < r; i++) {
                result.add(mutateStr(strV, tSet[i % 2]));
            }
        }
        return result;
    }
}

public abstract class AbstractParam {

    public static Logger logger = LogManager.getLogger(RESTCT.class);
    static final int randomCount = 3; // fixme: static int
    public String name;
    List<Object> defaultValues;
    Loc loc;
    boolean required;
    DataType type;
    DataType format;
    String description;
    public boolean isConstrained;
    public List<Pair<ValueType, Object>> domain;
    public Pair<ValueType, Object> value;
    boolean isReuse;

    public AbstractParam(String specifiedName, List<Object> defaultValues, Loc loc, boolean required, DataType paramType, DataType paramFormat, String description) {
        this.name = specifiedName;
        this.defaultValues = defaultValues;
        this.loc = loc;
        this.required = required;
        this.type = paramType;
        this.format = paramFormat;
        this.description = description;
        this.isConstrained = false;
        this.domain = new ArrayList<>();
        this.value = null;
        this.isReuse = false;
    }

    public Loc getLoc() {return  loc;}

    public String getName() {return  name;}

    public DataType getType() {return  type;}

    public abstract List<Object> genRandom();

    public List<AbstractParam> seeAllParameters() {
        List<AbstractParam> parameters = new ArrayList<>();
        if (this.name == null || this.name.equals("")) {
            return parameters;
        } else {
            parameters.add(this);
            return parameters;
        }
    }

    /**
     * 用四种方法其中之一生成parameter value domains
     * Dynamic Specification Success Random
     *
     * @param opStr          API     method.value + "***" + url
     * @param responseChains 响应链
     * @param okValues       value used in success http calls. key: operation._repr_+paramName
     * @return 返回值的描述
     */
    public List<AbstractParam> genDomain(String opStr, Map<String, Object> responseChains, Map<String, List<Pair<ValueType, ?>>> okValues) {
        if (this.isReuse) {
            // parameter 是一个被执行过的，domain是使用过的，需要进行变换
            // this.genReuseDomain();
        } else {
            if (this.loc == Loc.Path) {
                this.domain = this._getDynamicValues(opStr, responseChains);
            }
            if (this.domain.size() > 0) {
                return this.seeAllParameters();
            }

            if (this.defaultValues.size() > 0) {
                for (Object d : this.defaultValues) {
                    this.domain.add(new Pair<>(ValueType.Default, d));
                }
                if (this.domain.size() > 0) {
                    if (!this.required) {
                        this.domain.add(new Pair<>(ValueType.NULL, null));
                    }
                    //fixme: 也许可以改成seeAllParameters
                    return this.seeAllParameters();
                }
            } else if (okValues != null && okValues.size() > 0) {
                this.domain = this._getOkValue(opStr, okValues);
                if (this.domain.size() > 0) {
                    if (!this.required) {
                        this.domain.add(new Pair<ValueType, Object>(ValueType.NULL, null));
                    }
                    return this.seeAllParameters();
                }
            }

            List<Object> exampleValues = Example.findExample(this.name);
            exampleValues = exampleValues.subList(0, Math.min(2, exampleValues.size()));
            if (exampleValues.size() == 1) {
                this.domain.add(new Pair<>(ValueType.Random, Fuzzer.mutate(exampleValues.get(0), 2).get(0)   ) );
                this.domain.add(new Pair<>(ValueType.Default, exampleValues.get(0)));
            } else if (exampleValues.size() > 1) {
                ValueType[] vTSet = new ValueType[]{ValueType.Default, ValueType.Random};
                for (int i = 0; i < exampleValues.size(); i++) {
                    this.domain.add(new Pair<>(vTSet[i % 2], exampleValues.get(i)));
                }
            } else {
                List<Object> randomValue = this.genRandom();
                for (Object r : randomValue) {
                    this.domain.add(new Pair<>(ValueType.Random, r));
                }
            }

            if (!this.required) {
                this.domain.add(new Pair<>(ValueType.NULL, null));
            }
        }

        //以列表形式 返回当前对象， 为了后续直接extend参数列表
        List<AbstractParam> parameters = new ArrayList<>();
        parameters.add(this);
        return parameters;
    }

    private List<Pair<ValueType, Object>> _getOkValue(String opStr, Map<String, List<Pair<ValueType, ?>>> okValues) {
        String paramId = opStr + this.name;
        List<Pair<ValueType, Object>> domain = new ArrayList<>();
        List<Pair<ValueType, ?>> values = okValues.getOrDefault(paramId, new ArrayList<>());
        for (Pair<ValueType, ?> value : values) {
            domain.add((Pair<ValueType, Object>) value);
        }
        return domain;
    }

    /**
     * Dynamic  生成parameter value domains
     * 用responseChains里相似命名的output-parameters作为input-parameter的domain
     * 只用于Loc == 'path'的参数
     *
     * @param opStr          操作字符串
     * @param responseChains 响应链
     * @return List<Pair < ValueType, Object>> input-parameter values domain
     */
    private List<Pair<ValueType, Object>> _getDynamicValues(String opStr, Map<String, Object> responseChains) {
        assert this.loc == Loc.Path;
        assert this.name != null && !this.name.isEmpty();

        List<Pair<ValueType, Object>> dynamicValues = new ArrayList<>();
        Set<String> opSet = responseChains.keySet();

        Object[] lists = AbstractParam.analyseUrlRelation(opStr, opSet, this.name);
        List<String> highWeight = (List<String>) lists[0];
        List<String> lowWeight = (List<String>) lists[1];
        List<Object> responseValue = new ArrayList<>();

        for (String predecessor : highWeight) {
            Object response = responseChains.get(predecessor);
            double similarityMax = 0;
            int pathDepthMinimum = 10;
            List<String> rightPath = null;
            Object rightValue = null;

            for (Object[] pathSimilarityValue : AbstractParam.findDynamic(this.name, response, null)) {
                List<String> path = (List<String>) pathSimilarityValue[0];
                double similarity = (double) pathSimilarityValue[1];
                Object value = pathSimilarityValue[2];

                if (similarity > similarityMax) {
                    rightPath = path;
                    pathDepthMinimum = path.size();
                    similarityMax = similarity;
                    rightValue = value;
                } else if (similarity == similarityMax && path.size() < pathDepthMinimum) {
                    rightPath = path;
                    pathDepthMinimum = path.size();
                    rightValue = value;
                }
            }

            if (similarityMax > 0 && !responseValue.contains(rightValue)) {
                dynamicValues.add(new Pair<>(ValueType.Dynamic, new Pair<>(predecessor, rightPath)));
                responseValue.add(rightValue);
            }
        }

        return dynamicValues;
    }

    public static Map<String, Object> getRef(String ref, Map<String, Map<String, Object>> definitions) {
        // get the definition with the ref name
        //if not null, return Map<String, Object>


        Object tmp = ref.split("/")[ref.split("/").length - 1];
        Map<String, Object> res = definitions.getOrDefault(tmp, new HashMap<String, Object>());

        return res;
    }

    private static double match(String strA, String strB) {
        Pattern pattern = Pattern.compile("[-_]?id[-_]?", Pattern.CASE_INSENSITIVE);
        Matcher matcherA = pattern.matcher(strA);
        if (matcherA.find()) {
            strA = "id";
        }
        Matcher matcherB = pattern.matcher(strB);
        if (matcherB.find()) {
            strB = "id";
        }


        String sanitizedA = strA.replaceAll("[^a-zA-Z0-9]", "");
        String sanitizedB = strB.replaceAll("[^a-zA-Z0-9]", "");
        int distance = LevenshteinDistance.getDefaultInstance().apply(sanitizedA.toLowerCase(), sanitizedB.toLowerCase());
        int totalLength = sanitizedA.length() + sanitizedB.length();
        return Math.round(((double) totalLength - distance) / totalLength * 100.0) / 100.0;
    }

    private static Object[] analyseUrlRelation (String opStr, Set < String > opSet, String paramName){
        List<String> highWeight = new ArrayList<>();
        List<String> lowWeight = new ArrayList<>();
        String url = opStr.split("\\*\\*\\*")[1];
        for (String candidate : opSet) {
            String otherUrl = candidate.split("\\*\\*\\*")[1];
            if (otherUrl.trim().equals(url.split("\\{" + paramName + "\\}")[0].trim())) {
                highWeight.add(candidate);
            } else if (otherUrl.trim().equals(url.split("\\{" + paramName + "\\}")[0].trim() + "/{" + paramName + "}")) {
                highWeight.add(candidate);
            } else {
                lowWeight.add(0, candidate);
            }
        }
        return new Object[]{highWeight, lowWeight};
    }

    private static List<Object[]> findDynamic(String paramName, Object response, List<String> path) {
        String name = null;
        Pattern pattern = Pattern.compile("[-_]?id[-_]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(paramName);
        if (matcher.find()) {
            paramName = "id";
        }
        if (path == null) {
            path = new ArrayList<String>();
        }
        List<Object[]> results = new ArrayList<>();
        if (response instanceof List) {
            List<Object> responseList = (List<Object>) response;
            List<String> localPath = new ArrayList<>(path);
            if (!responseList.isEmpty()) {
                for (Object obj : responseList) {
                    for (Object[] result : findDynamic(paramName, obj, localPath)) {
                        results.add(result);
                    }
                }
            }
        } else if (response instanceof Map) {
            Map<String, Object> responseMap = (Map<String, Object>) response;
            for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                List<String> localPath = new ArrayList<String>(path);
                double similarity = match(paramName, key);
                if (similarity > 0.9) {
                    localPath.add(key);
                    Object[] result = {localPath, similarity, value};
                    results.add(result);
                } else if (value instanceof List || value instanceof Map) {
                    localPath.add(key);
                    for (Object[] result : findDynamic(paramName, value, new ArrayList<String>(localPath))) {
                        results.add(result);
                    }
                }
            }
        }
        return results;
    }

    public Object printableValue(Map<String, Object>  response) {
        if (this.value == null) {
            return null;
        }
        ValueType valueType = this.value.getKey();
        Object value = this.value.getValue();
        if (valueType == ValueType.Random) {
            value = Fuzzer.mutate(value, 1).get(0);
        }
        if (valueType == ValueType.Dynamic) {
            Pair<String, List<String>> dynamicPair = (Pair<String, List<String>>) value; // todo: 不确定这个value类型会是什么
            String opStr = dynamicPair.getKey();
            List<String> path = dynamicPair.getValue();
            Object dynamicResponse = response.get(opStr);
            value = _assembleDynamic(path, dynamicResponse);

        }
        return value;
    }

    private static Object _assembleDynamic(List<String> path, Object response) {
        Object value = response;
        for (String p : path) {
            if (value instanceof List) {
                try {
                    value = ((List) value).get(0);
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
            }
            try {
                value = ((Map) value).get(p);
            } catch (ClassCastException | NullPointerException e) {
                return null;
            }

            if (value == null) {
                return null;
            }
        }
        return value;
    }


    public String toString() {
        return this.name;
    }

    public boolean isEssential() {
        return this.isConstrained || this.required;
    }

    public List<Pair<ValueType, Object>> getDomain() {
        return domain;
    }

    public String getDescription() {
        return description;
    }
}

class ObjectParam extends AbstractParam{
    private List<AbstractParam> children;

    public ObjectParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                       DataType paramFormat, String description, List<AbstractParam> children) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
        this.children = children;
    }

    public static AbstractParam buildObject(Map<String, Object> info, Map<String, Map<String, Object>> definitions) throws UnsupportedError {
        Map<String, Object> childrenInfo = (Map<String, Object>) info.remove("allInfo");
        String[] keywords = {"allOf", "oneOf", "anyOf", "additionalProperties"};
        for (String key : keywords) {
            if (childrenInfo.containsKey(key)) {
                Object value = childrenInfo.get(key);
                if (value instanceof List) {
                    value = ((List<Object>) value).get(new Random().nextInt(((List<Object>) value).size()));
                }
                if (value instanceof Map) {
                    Map<String, Object> childInfo = (Map<String, Object>) value;
                    if (childInfo.containsKey(DocKey.REF_SIGN)) {
                        String ref = (String) childInfo.get(DocKey.REF_SIGN);
                        value = AbstractParam.getRef(ref, definitions);
                    }
                }
                childrenInfo = (Map<String, Object>) value;
            }
        }

        List<AbstractParam> children = new ArrayList<>();
        Map<String, Object> properties = (Map<String, Object>) childrenInfo.get(DocKey.PROPERTIES);
        for (String pName : properties.keySet()) {
            Map<String, Object> pInfo = (Map<String, Object>) properties.get(pName);
            children.add(Parameter.buildParam(pInfo, definitions, pName));
        }

        info.put("children", children);

        boolean par_required;//这个对象是否必需，根据之前逻辑，如果它"required"字段就是List
        if (childrenInfo.get("required") instanceof List) {
            List<String> required = (List<String>) info.get("required");
            if (required != null) {
                for (AbstractParam child : children) {
                    if (required.contains(child.name)) {
                        child.required = true;
                    }
                }
            }

            par_required = true;
        } else {
            par_required = false;
        }

        return new ObjectParam(info.get("specifiedName").toString(), (List<Object>) info.get("default"), (Loc) info.get("loc"),
                par_required, (DataType) info.get("paramType"), (DataType) info.get("paramFormat"), info.get("description").toString(),children);
    }

    @Override
    public List<AbstractParam> seeAllParameters() {
        List<AbstractParam> allParameters = new ArrayList<>();
        allParameters.add(this);
        for (AbstractParam child : this.children) {
            allParameters.addAll(child.seeAllParameters());
        }
        return allParameters;
    }

    @Override
    public List<AbstractParam> genDomain(String opStr, Map<String, Object> responseChains, Map<String, List<Pair<ValueType, ?>>> okValues) {
        List<AbstractParam> paramList = new ArrayList<>();
        for (AbstractParam parameter : this.seeAllParameters()) {
            if (parameter != this) {
                paramList.addAll(parameter.genDomain(opStr, responseChains, okValues));
            }
        }
        return this.seeAllParameters();

    }

    @Override
    public List<Object> genRandom() {
        return null;
    }

    @Override
    public Object printableValue(Map<String, Object> response ) {

        Map<String, Object> value = new HashMap<>();
        for (AbstractParam child : this.children){
            Object childValue = child.printableValue(response);
            if (childValue != null)
                value.put(child.name, childValue);
        }

        return value.isEmpty() ? null : value;
    }
}

class ArrayParam extends AbstractParam {

    private AbstractParam itemParam;
    private int maxItems;
    private int minItems;
    private boolean unique;

    public ArrayParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType, DataType paramFormat, String description,
                      AbstractParam itemParam, int minItems, int maxItems, boolean unique) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
        this.itemParam = itemParam;
        this.itemParam.required = required;
        //this.itemParam.isConstrained = isConstrained; fixme:我觉得这句不需要
        this.maxItems = maxItems;
        this.minItems = minItems;
        this.unique = unique;

        assert this.minItems <= this.maxItems;
    }

    public static ArrayParam buildArray(Map<String, Object> info, Map<String, Map<String, Object>> definitions) throws UnsupportedError {
        Object itemInfo = info.remove(ParamKey.ITEMS);
        if (itemInfo == null) {
            throw new UnsupportedError(info + " can not be transferred to ArrayParam");
        } else if (itemInfo instanceof Map) {
            Map<String, Object> itemMap = (Map<String, Object>) itemInfo;
            if (itemMap.containsKey(ParamKey.TYPE)) {
                AbstractParam itemParam = Parameter.buildParam(itemMap, definitions, info.get("specifiedName").toString());
                info.put("itemParam", itemParam);
                return new ArrayParam(info.get("specifiedName").toString(), (List<Object>) info.get("default"), (Loc) info.get("loc"),
                        (boolean) info.get("required"), (DataType) info.get("paramType"), (DataType) info.get("paramFormat"), info.get("description").toString(),
                        itemParam, (int) info.get("minItems"), (int) info.get("maxItems"), (boolean) info.get("unique"));
            } else if (itemMap.containsKey(DocKey.REF_SIGN)) {
                String refInfo = itemMap.get(DocKey.REF_SIGN).toString();
                Map<String, Object> itemInfoCopied = new HashMap<>();
                itemInfoCopied.putAll(itemMap);
                itemInfoCopied.putAll((Map<? extends String, ?>) AbstractParam.getRef(refInfo, definitions));
                AbstractParam itemParam = Parameter.buildParam(itemInfoCopied, definitions, info.get("specifiedName").toString());
                info.put("itemParam", itemParam);
                return new ArrayParam(info.get("specifiedName").toString(), (List<Object>) info.get("default"), (Loc) info.get("loc"),
                        (boolean) info.get("required"), (DataType) info.get("paramType"), (DataType) info.get("paramFormat"), info.get("description").toString(),
                        itemParam, (int) info.get("minItems"), (int) info.get("maxItems"), (boolean) info.get("unique"));
            } else {
                throw new UnsupportedError(info + " can not be transferred to ArrayParam");
            }
        } else {
            throw new UnsupportedError(info + " can not be transferred to ArrayParam");
        }

    }

    @Override
    public List<AbstractParam> seeAllParameters() {
        return itemParam.seeAllParameters();
    }

    @Override
    public List<AbstractParam> genDomain(String opStr, Map<String, Object> responseChains, Map<String, List<Pair<ValueType, ?>>> okValues) {
        return itemParam.genDomain(opStr, responseChains, okValues);
    }

    @Override
    public List<Object> genRandom() {
        return null;
    }

    @Override
    public Object printableValue(Map<String, Object> response ) {
        Object value = this.itemParam.printableValue(response);
        if (value == null) {
            return null;
        } else {
            List<Object> valueAsList = new ArrayList<>();
            valueAsList.add(value);
            return valueAsList;
        }
    }

}

class BoolParam extends AbstractParam {
    private List<Object> enumValues;

    public BoolParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required,
                     DataType paramType, DataType paramFormat, String description) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
        this.enumValues = Arrays.asList(true, false);
    }

    @Override
    public List<Object> genRandom() {
        //do nothing
        return null;
    }

    @Override
    public List<AbstractParam> genDomain(String opStr, Map<String, Object> responseChains, Map<String, List<Pair<ValueType, ?>>> okValues) {
        if(this.domain.size() > 0)
            return this.seeAllParameters();
        this.domain.add(new Pair<>(ValueType.Enum, false));
        this.domain.add(new Pair<>(ValueType.Enum, true));
        if (!this.required) {
            this.domain.add(new Pair<>(ValueType.NULL, null));
        }
        return this.seeAllParameters();
    }

}

class FileParam extends AbstractParam {
    public FileParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                     DataType paramFormat, String description) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
    }

    @Override
    public List<Object> genRandom() {
        List<Object> randomValues = new ArrayList<>();
        String[] options = {"", "random", "long random long random"};
        randomValues.add(options[new Random().nextInt(options.length)]);
        return randomValues;
    }

    @Override
    public Object printableValue(Map<String, Object> response ) {
        Object value = super.printableValue(response);
        if (this.value == null) {
            return null;
        } else {
            ValueType valueType = this.value.getKey();

            if (valueType == ValueType.Random) {
                Map<String, Object[]> bodyValue = new HashMap<>();
                 bodyValue.put("file", new Object[]{"random.txt", value});
                return bodyValue;
            } else {
                return value;
            }

        }
    }
}

class NumberParam extends AbstractParam {
    private double maximum;
    private double minimum;
    private boolean exclusiveMinimum;
    private boolean exclusiveMaximum;
    private double multipleOf;

    public NumberParam(String specifiedName, List<Object> defaultValue, Loc loc, boolean required, DataType paramType,
                       DataType paramFormat, String description, double maximum, double minimum, boolean exclusiveMinimum,
                       boolean exclusiveMaximum, double multipleOf) {
        super(specifiedName, defaultValue, loc, required, paramType, paramFormat, description);
        this.maximum = maximum;
        this.minimum = minimum;
        this.exclusiveMinimum = exclusiveMinimum;
        this.exclusiveMaximum = exclusiveMaximum;
        this.multipleOf = multipleOf;
        assert this.minimum <= this.maximum;
    }

    @Override
    public List<Object> genRandom() {
        double maxV = this.exclusiveMaximum ? this.maximum - 1 : this.maximum;
        double minV = this.exclusiveMinimum ? this.minimum + 1 : this.minimum;

        List<Object> randomValues = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < AbstractParam.randomCount; i++) {
            if (this.type == DataType.Double || this.type == DataType.Float) {
                double randomValue = minV + (maxV - minV) * random.nextDouble();
                randomValues.add(randomValue);
            } else {
                int randomValue = (int) (minV + (maxV - minV + 1) * random.nextDouble());
                randomValues.add(randomValue);
            }
        }

        if (this.multipleOf != 0) {
            randomValues.replaceAll(n -> {
                if (n instanceof Double) {
                    return ((Double) n) * this.multipleOf;
                } else {
                    return ((Integer) n) * (int) this.multipleOf;
                }
            });
        }

//        AbstractParam.logger.debug("NumberParam genDomain:{} - {}", this.name, randomValues );
        return randomValues;
    }
}

class StringParam extends AbstractParam {
    private int maxLength;
    private int minLength;

    public StringParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                       DataType paramFormat, String description, int maxLength, int minLength) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
        this.maxLength = maxLength;
        this.minLength = minLength;
        assert this.minLength <= this.maxLength;
    }

    @Override
    public List<Object> genRandom() {
        List<Object> randomValues = new ArrayList<>();
        Random random = new Random();
        String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";//随机生成的36个字符
        for (int i = 0; i < AbstractParam.randomCount; i++) {
            String value = "";
            int length = random.nextInt(37);
            for (int j = 0; j < length; j++) {
                char c = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));
                value += c;
            }
            if (this.format == DataType.Binary) {
                randomValues.add(value.getBytes(StandardCharsets.UTF_8));
            } else if (this.format == DataType.Byte) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                randomValues.add(base64);
            } else {
                randomValues.add(value);
            }
        }
        return randomValues;
    }
}

class Date extends AbstractParam {
    public Date(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                DataType paramFormat, String description) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
    }

    @Override
    public List<Object> genRandom() {
        LocalDate curTime = LocalDate.now();
        return Date.getMutate(curTime);
    }

    public static List<Object> getMutate(LocalDate timeDto) {
        String timeFormat = "yyyy-MM-dd";
        timeDto = timeDto == null? LocalDate.now() : timeDto;
        List<Object> randomValues = new ArrayList<>();
        randomValues.add(new Object[]{ValueType.Random, timeDto.format(DateTimeFormatter.ofPattern(timeFormat))});
        for (int i = 0; i < AbstractParam.randomCount - 1; i++) {
            if (i % 2 == 0) {
                randomValues.add(new Object[]{ValueType.Random, timeDto.minusDays(i + 1).format(DateTimeFormatter.ofPattern(timeFormat))});
            } else {
                randomValues.add(new Object[]{ValueType.Random, timeDto.plusDays(i + 1).format(DateTimeFormatter.ofPattern(timeFormat))});
            }
        }
        return randomValues;
    }

    @Override
    public Object printableValue(Map<String, Object>  response) {
        Object value = super.printableValue(response);
        if (this.value == null) {
            return null;
        } else {
            ValueType valueType = this.value.getKey();
            if (valueType == ValueType.Random) {
                try {
                    value = Date.getMutate(LocalDate.parse(value.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))).get(0);
                } catch (Exception e) {
                    value = Date.getMutate(null).get(0);
                }
            }
            return value;
        }
    }
}

class UuidParam extends AbstractParam {
    public UuidParam(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                     DataType paramFormat, String description) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
    }

    @Override
    public List<Object> genRandom() {
        return null; // implementation of UUID generation goes here
    }
}

class DateTime extends AbstractParam {
    public DateTime(String specifiedName, List<Object> defaultVal, Loc loc, boolean required, DataType paramType,
                    DataType paramFormat, String description) {
        super(specifiedName, defaultVal, loc, required, paramType, paramFormat, description);
    }

    @Override
    public List<Object> genRandom() {
        LocalDateTime curTime = LocalDateTime.now();
        return DateTime.getMutate(curTime);
    }

    public static List<Object> getMutate(LocalDateTime timeDto) {
        List<Object> randomValues = new ArrayList<>();
        randomValues.add(ValueType.Random);
        randomValues.add(timeDto.truncatedTo(ChronoUnit.SECONDS).toString());

        for (int i = 0; i < AbstractParam.randomCount - 1; i++) {
            randomValues.add(ValueType.Random);
            if (i % 2 == 0) {
                randomValues.add(timeDto.minusDays(i + 1).truncatedTo(ChronoUnit.SECONDS).toString());
            } else {
                randomValues.add(timeDto.plusDays(i + 1).truncatedTo(ChronoUnit.SECONDS).toString());
            }
        }

        return randomValues;
    }

    @Override
    public Object printableValue(Map<String, Object> response) {
        Object value = super.printableValue(response);
        if (this.value == null) {
            return null;
        } else {
            return value;
        }
    }
}
