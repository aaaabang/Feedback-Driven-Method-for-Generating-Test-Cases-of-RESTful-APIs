package org.restct.dto.param;

import javafx.util.Pair;
import org.restct.dto.keywords.DataType;
import org.restct.dto.keywords.Loc;
import org.restct.dto.keywords.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnumParam extends AbstractParam {
    private List<Object> enumValues;

    public EnumParam(String specifiedName, List<Object> defaultValues, Loc loc, boolean required, DataType paramType,
                     DataType paramFormat, String description, List<Object> enumValues) {
        super(specifiedName, defaultValues, loc, required, paramType, paramFormat, description);
        this.enumValues = enumValues;
    }

    @Override
    public List<AbstractParam> genDomain(String opStr, Map<String, Object> responseChains, Map<String, List<Pair<ValueType, ?>>> okValues) {
        if(this.domain.size() > 0)
            return this.seeAllParameters();
        for (Object value : enumValues) {
//            if (this.domain.size() > 10)//限制一下时间
//                break;
            this.domain.add(new Pair<ValueType, Object>(ValueType.Enum, value));
        }
        if (!this.required) {
            this.domain.add(new Pair<ValueType, Object>(ValueType.NULL, value));
        }
        return this.seeAllParameters();
    }

    @Override
    public List<Object> genRandom() {
        return null;
    }

    public List<String> getEnum() {
        return enumValues.stream().map(Object::toString).collect(Collectors.toList());
    }
}
