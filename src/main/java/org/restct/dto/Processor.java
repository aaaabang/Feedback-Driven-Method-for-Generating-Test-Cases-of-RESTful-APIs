//package org.restct.dto;
//
//import com.google.gson.Gson;
//import com.google.gson.reflect.TypeToken;
//import edu.stanford.nlp.ling.CoreAnnotations;
//import edu.stanford.nlp.ling.CoreLabel;
//import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
//import edu.stanford.nlp.pipeline.*;
//import edu.stanford.nlp.util.CoreMap;
//import org.restct.Config;
//import org.restct.dto.param.AbstractParam;
//import org.restct.dto.param.EnumParam;
//
//import java.io.*;
//import java.lang.reflect.Type;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
//import static org.restct.RESTCT.logger;
//
//public class Processor {
//    private StanfordCoreNLP pipeline;
//    Properties props;
//
//    private Set<AbstractParam> paramEntities;
//    private List<String> descriptions;
//    private Map<String, AbstractParam> paramNames;
//    private Map<String, List<String>> paramValues;
//
//    public Processor(Set<AbstractParam> parameterList) throws FileNotFoundException {
//
////        Properties props = new Properties();
////        props.load(new FileReader("path/to/constraint-matcher.props"));
////        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
//
//        props = new Properties();
//        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
//        props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
//        this.pipeline = new StanfordCoreNLP(props);
//
//        this.paramEntities = parameterList;
//        this.descriptions = paramEntities.stream().map(p -> p.getDescription()).collect(Collectors.toList());
//        this.paramNames = paramEntities.stream().collect(Collectors.toMap(p -> p.getName(), p -> p));
//        assert this.paramNames.size() == paramEntities.size();
//
//        //参数如果是自定义枚举类型
//        this.paramValues = new HashMap<String, List<String>>();
//        for (AbstractParam param : paramEntities) {
//            if (param instanceof EnumParam) {
//                this.paramValues.put(param.getName(), ((EnumParam) param).getEnum());
//            }
//        }
//        setPipeline();
//
//    }
//
//    private void setPipeline(){
//        File tempFile = null;
//        try {
//            tempFile = File.createTempFile("custom", ".rules");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        PrintWriter pw = null;
//        try {
//            pw = new PrintWriter(tempFile);
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//
//        List<CoreMap> patterns = new ArrayList<CoreMap>();
//        for (String paramName : this.paramNames.keySet()) {
//            CoreMap pattern = new CoreLabel();
//            pattern.set(CoreAnnotations.AnswerAnnotation.class, EntityLabel.Param.getValue());
//            pattern.set(CoreAnnotations.TextAnnotation.class, paramName);
//            pattern.set(CoreAnnotations.NamedEntityTagAnnotation.class, EntityLabel.Param.getValue());
//            patterns.add(pattern);
//
//            pw.println(paramName + "\t" + EntityLabel.Param.getValue());
//        }
//
//        for (List<String> valueSet : this.paramValues.values()) {
//            for (String value : valueSet) {
//                CoreMap pattern = new CoreLabel();
//                pattern.set(CoreAnnotations.AnswerAnnotation.class, EntityLabel.Value.getValue());
//                pattern.set(CoreAnnotations.TextAnnotation.class, value);
//                pattern.set(CoreAnnotations.NamedEntityTagAnnotation.class, EntityLabel.Value.getValue());
//                patterns.add(pattern);
//
//                pw.println(value + "\t" + EntityLabel.Param.getValue());
//
//            }
//        }
//
//        pw.close();
//
//        // 将自定义规则写入文件中
//
//
//
//
//        props.setProperty("regexner.mapping", "custom.rules");
////        props.setProperty("tokensregex.rules", "tokensregex.rules");
////        props.setProperty("tokensregex.matchedExpressionsAnnotationKey",
////                "edu.stanford.nlp.examples.TokensRegexAnnotatorDemo$MyMatchedExpressionAnnotation");
//
//        pipeline = new StanfordCoreNLP(props);
//        pipeline.addAnnotator(new RegexNERAnnotator(props.getProperty("regexner.mapping")));
//    }
//
//    private String cleanText(String text) {
//        Annotation document = new Annotation(text);
//        this.pipeline.annotate(document);
//        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
//        List<String> words = new ArrayList<String>();
//        for (CoreLabel token : tokens) {
//            words.add(token.word());
//        }
//        return String.join(" ", words);
//    }
//
//    public List<Constraint> parse() {
//        List<Constraint> constraints = new ArrayList<>();
//        for (String text: this.descriptions){
//            logger.debug("Processor.parse text_beforeClean: ", text);
//            text = cleanText(text);
//            logger.debug("Processor.parse text_afterClean", text);
//
//            Annotation document = new Annotation(text);
//            pipeline.annotate(document);
//
//
//            List<String> involvedParamNames = new ArrayList<>();
//            List<String> involvedValues = new ArrayList<>();
//            List<String> ents = new ArrayList<>();
//            // 遍历每个单词，输出命名实体信息
//            List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
//            for(CoreMap sentence: sentences) {
//                System.out.println(sentence.toString());
//
//                for(CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//                    String name = token.word();
//                    String nerTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
//                    if (nerTag == EntityLabel.Param.getValue()) {
//                        involvedParamNames.add(name);
//                        ents.add(name);
//                    } else if (nerTag == EntityLabel.Value.getValue())
//                    logger.debug("Token: " + token.word() + " | NER tag: " + nerTag);
//
//                }
//
////                if (sentence.containsKey("customAnnotation")) {
////                    System.out.println(sentence.get("customAnnotation"));
////                }
//
//            }
//
//            TokensRegexMatcher matcher = TokensRegexMatcher.getMatcherFromFiles("tokensregex.rules");
//
//            // 匹配模式并获取结果
//            List<MatchedExpression> matches = matcher.findNonOverlappingMatches(tokens);
//            for (MatchedExpression match : matches) {
//                System.out.println("Matched expression: " + match.getText());
//                System.out.println("Pattern name: " + match.getAnnotation("name"));
//            }
//        }
//
//        this.updateParam(constraints);
//
//        return constraints;
//    }
//
//    private void updateParam(List<Constraint> constraints) {
//        Set<String> paramNames = new HashSet<>();
//        for (Constraint c: constraints) {
//            paramNames.addAll(c.getParamNames());
//        }
//
//        for(AbstractParam p: this.paramEntities) {
//            if (paramNames.contains(p.name)){
//                p.isConstrained = true;
//            } else {
//                p.isConstrained = false;
//            }
//        }
//    }
//}
//
//enum EntityLabel {
//    Param( "PARAM" ),
//    Value( "VALUE" );
//
//    private final String value;
//
//    EntityLabel(String value) {
//        this.value = value;
//    }
//
//    public String getValue() {
//        return value;
//    }
//}
//
//
//class ConstraintMatcherAnnotator extends Matcher {
//
//    private Matcher matcher;
//    private Set<List<String>> spanWithConstraints;
//
//
//    public ConstraintMatcherAnnotator(String name, Properties props) throws IOException {
//        super();
//        this.matcher = new Matcher(props);
//        _loadPatterns();
//        this.spanWithConstraints = new HashSet<>();
//    }
//
//    private void _loadPatterns() throws IOException {
//        Gson gson = new Gson();
//        String json = new String(Files.readAllBytes(Paths.get(Config.patterns)), StandardCharsets.UTF_8);
//        Type type = new TypeToken<Map<String, List<Map<String, Object>>>>(){}.getType();
//        Map<String, List<Map<String, Object>>> patterns = gson.fromJson(json, type);
//
//        Map<List<String>, List<String>> rules = new HashMap<>();
//        for (List<Map<String, Object>> ruleList : patterns.values()) {
//            for (Map<String, Object> ruleInfo : ruleList) {
//                List<String> constraint = (List<String>) ruleInfo.get("constraint");
//                List<String> patternList = (List<String>) ruleInfo.get("pattern");
//                rules.computeIfAbsent(constraint, k -> new ArrayList<>()).addAll(patternList);
//
//                TokensRegexPattern pattern = TokensRegexPattern.compile("pattern");
//
//            }
//        }
//
//        for (Map.Entry<List<String>, List<String>> entry : rules.entrySet()) {
//            List<String> constraint = entry.getKey();
//            List<String> patternList = entry.getValue();
//            Pattern[] patterns = patternList.stream().map(Pattern::compile).toArray(Pattern[]::new);
//            this.matcher.add(constraintStr, patterns);
//        }
//    }
//
//    @Override
//    public void annotate(Annotation annotation) {
//        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
//        for (CoreMap sentence : sentences) {
//            List<MatchedSpan> matches = this.matcher.match(sentence);
//            for (MatchedSpan match : matches) {
//                List<CoreMap> tokens = match.getTokens();
//                Tuple<String> entries = new Tuple<>();
//                for (CoreMap token : tokens) {
//                    String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
//                    entries.add(ne);
//                }
//                if (!this.spanWithConstraints.contains(entries)) {
//                    sentence.set(CoreAnnotations.ConstraintAnnotation.class, match.getId());
//                    this.spanWithConstraints.add(entries);
//                }
//            }
//        }
//    }
//
//    @Override
//    public Set<Requirement> requires() {
//        return Collections.singleton(TOKENIZE_REQUIREMENT);
//    }
//
//    @Override
//    public Set<Requirement> requirementsSatisfied() {
//        return Collections.singleton(CONSTRAINT_REQUIREMENT);
//    }
//
//    @Override
//    public CoreMapAttributeAggregator getAttributeAggregator() {
//        return null;
//    }
//}
