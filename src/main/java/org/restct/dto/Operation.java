package org.restct.dto;

import javafx.util.Pair;
import org.restct.dto.keywords.Method;
import org.restct.dto.keywords.ValueType;
import org.restct.dto.param.AbstractParam;

import java.util.*;

public class Operation {
    public static List<Operation> members;
    public final String url;
    public final Method method;
    public final Set<AbstractParam> parameterList;
    private final Set<Response> responseList;
    private final List<Constraint> constraints;

    static {
        members = new ArrayList<>();
    }

    public Operation(String url, String methodName) {
        this.url = url;
        this.method = Method.fromValue(methodName);
        this.parameterList = new HashSet<>();
        this.responseList = new HashSet<>();
        this.constraints = new ArrayList<>();
        members.add(this);
    }

    public String getUrl(){
        return url;
    }

    public Method getMethod() { return method; }

    public Set<AbstractParam> getParameterList() {
        return parameterList;
    }

    public void addParam(AbstractParam abstractParam) {
        this.parameterList.add(abstractParam);
    }

    public void addResponse(Response response) {
        this.responseList.add(response);
    }


    public List<AbstractParam> genDomain(Map<String, Object> responseChain, Map<String, List<Pair<ValueType, ?>>> okValues) {
        List<AbstractParam> paramList = new ArrayList<>();
        for (AbstractParam param : parameterList) {
            paramList.addAll(param.genDomain(this.toString(), responseChain, okValues));
        }
        return paramList;
    }

    public static List<Operation> getMembers() {
        return members;
    }

    public Set<Pair<String, Integer>> getSplittedUrl() {
        Set<Pair<String, Integer>> splittedUrl = new HashSet<>();
        String[] parts = url.split("/");
        for (int i = 0; i < parts.length; i++) {
            splittedUrl.add(new Pair<>(parts[i], i));
        }
        return splittedUrl;
    }

    public void addConstraints(List<Constraint> constraints) {
        this.constraints.addAll(constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operation operation = (Operation) o;
        return Objects.equals(url, operation.url) &&
                method == operation.method;
    }

    @Override
    public String toString() {
        return method.toString() + "***" + url;
    }


    public List<Constraint> getConstraints() {
        return constraints;
    }
}
