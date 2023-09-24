package org.restct.dto;


import javafx.util.Pair;
import org.restct.dto.keywords.ValueType;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.restct.RESTCT.logger;

public class Constraint {
    private String template;
    private List<String> paramNames;
    private List<String> valueStr;
    private List<String> ents;

    public Constraint(String template, List<String> paramNames, List<String> valueStr, List<String> ents) {
        this.template = template;
        this.paramNames = paramNames;
        this.valueStr = valueStr;
        this.ents = ents;
    }

    public String toActs(Map<String, List<Pair<ValueType, Object>>> valueDict) {
        String formattedStr = this.template;
        Matcher matcher = Pattern.compile("(\\w)\\1\\s*(!?=)\\s*[\\'\"]?(None|(\\w)\\4)[\\'\"]?").matcher(this.template);
//        while (matcher.find()) {
//            String group3 = matcher.group(3);
//            String paramName, op;
//            Object value;
//            logger.debug("Constraints toActs matcher:{}", matcher);
//            if (group3.equals("None")) {
//                paramName =ents.get(matcher.group(1).charAt(0) - 65);
//                op = matcher.group(2);
//                value = null;
//            } else {
//                paramName = ents.get(matcher.group(1).charAt(0) - 65);
//                op = matcher.group(2);
//                value = this.ents.get(matcher.group(4).charAt(0) - 65);
//            }
//            try {
//                List<Object> valueList = Arrays.stream(valueDict.get(paramName)).map(v -> ((Pair<ValueType, Object>)v).getValue()).collect(Collectors.toList());
//                int valueIndex = valueList.indexOf(value);
//                formattedStr = matcher.replaceAll(paramName + " " + op + " " + valueIndex);
//
//                formattedStr = formattedStr.replaceAll(
//                        Matcher.quoteReplacement(matcher.group()),
//                        paramName + " " + op + " " + valueIndex
//                );
//            } catch (NullPointerException | ClassCastException | IndexOutOfBoundsException e) {
//                return null;
//            }
//        }
        return formattedStr;
    }

    public List<String> getParamNames() {
        return paramNames;
    }
}

