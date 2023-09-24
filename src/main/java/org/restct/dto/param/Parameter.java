package org.restct.dto.param;

import org.restct.dto.keywords.DataType;
import org.restct.dto.keywords.DocKey;
import org.restct.dto.keywords.Loc;
import org.restct.dto.keywords.ParamKey;
import org.restct.exception.UnsupportedError;

import java.util.*;

public class Parameter {
    public static AbstractParam buildParam(Map<String, Object> info, Map<String, Map<String, Object>> definitions, String specifiedName) throws UnsupportedError {

            Map<String, Object> buildInfo = new HashMap<>();
            buildInfo.put("specifiedName", specifiedName == null ? info.getOrDefault(ParamKey.NAME, "") : specifiedName);
            buildInfo.put("paramType", info.get(ParamKey.TYPE) == null ? DataType.NULL : DataType.fromValue((String) info.get(ParamKey.TYPE)));
            buildInfo.put("paramFormat", info.get(ParamKey.FORMAT) == null ? DataType.NULL : DataType.fromValue(info.get(ParamKey.FORMAT).toString().replace("-", "")));
            buildInfo.put("default", info.get(ParamKey.DEFAULT) != null ? Arrays.asList(info.get(ParamKey.DEFAULT)) : new ArrayList<>());
            buildInfo.put("loc", info.get(ParamKey.LOCATION) == null ? Loc.NULL : Loc.fromValue((String) info.get(ParamKey.LOCATION)));
            buildInfo.put("required", info.get(ParamKey.REQUIRED) != null ? info.get(ParamKey.REQUIRED) : false);
            buildInfo.put("description", info.get(ParamKey.DESCRIPTION) != null ? info.get(ParamKey.DESCRIPTION) : "");

            List<Object> paramEnum = (List<Object>) info.get(ParamKey.ENUM);
            if (buildInfo.get("paramType").equals(DataType.NULL)) {
                if (info.containsKey(ParamKey.SCHEMA)) {
                    Map<String, String> schema = (Map<String, String>) info.get(ParamKey.SCHEMA);
                    Map<String, Object> extraInfo = AbstractParam.getRef(schema.getOrDefault(DocKey.REF_SIGN, null), definitions);

                    //buildInfo.put("ref", extraInfo);
                    if((boolean) info.get("required")){
                        info.putAll(extraInfo);
                    } else {
                        extraInfo.putAll(info);
                        info = extraInfo;
                    }

                    return buildParam(info, definitions, null);
                }
                else if (info.containsKey(DocKey.REF_SIGN)) {
                    Map<String, Object> extraInfo = (Map<String, Object>) AbstractParam.getRef((String)info.get(DocKey.REF_SIGN), definitions);
                    extraInfo.putAll(info);
                    return buildParam(extraInfo, definitions, specifiedName);
                }
                else if (info.containsKey(DocKey.ALL_OF) || info.containsKey(DocKey.ANY_OF) || info.containsKey(DocKey.ONE_OF) || info.containsKey(DocKey.ADDITIONAL_PROPERTIES)) {
                    return null;
                }
                else {
                    throw new UnsupportedError(info.toString());
                }
            }
            else if (paramEnum != null) {
                buildInfo.put("enum", paramEnum);
                return new EnumParam(
                        (String) buildInfo.get("specifiedName"),
                        (List<Object>) buildInfo.get("default"),
                        (Loc) buildInfo.get("loc"),
                        (boolean) buildInfo.get("required"),
                        (DataType) buildInfo.get("paramType"),
                        (DataType) buildInfo.get("paramFormat"),
                        (String) buildInfo.get("description"),
                        (List<Object>) buildInfo.get("enum")
                );
            }
            else if (Arrays.asList(DataType.Double, DataType.Integer, DataType.Number, DataType.Int32, DataType.Int64, DataType.Float, DataType.Long).contains(buildInfo.get("paramType"))) {
                buildInfo.put("maximum", info.get(ParamKey.MAXIMUM) != null ? Double.valueOf(info.get(ParamKey.MAXIMUM).toString()) : Double.valueOf(100));
                buildInfo.put("minimum", info.get(ParamKey.MINIMUM) != null ? Double.valueOf(info.get(ParamKey.MINIMUM).toString()) : Double.valueOf(0));
                buildInfo.put("exclusiveMinimum", info.get(ParamKey.EXCLUSIVEMINIMUM) != null ? (Boolean)info.get(ParamKey.EXCLUSIVEMINIMUM) : false);
                buildInfo.put("exclusiveMaximum", info.get(ParamKey.EXCLUSIVEMAXIMUM) != null ? (Boolean)info.get(ParamKey.EXCLUSIVEMAXIMUM) : false);
                return new NumberParam(
                        (String) buildInfo.get("specifiedName"),
                        (List<Object>) buildInfo.get("default"),
                        (Loc) buildInfo.get("loc"),
                        (boolean) buildInfo.get("required"),
                        (DataType) buildInfo.get("paramType"),
                        (DataType) buildInfo.get("paramFormat"),
                        (String) buildInfo.get("description"),
                        (double) buildInfo.get("maximum"),
                        (double) buildInfo.get("minimum"),
                        (boolean) buildInfo.get("exclusiveMinimum"),
                        (boolean) buildInfo.get("exclusiveMaximum"),
                        0
                );
            }
            else if (buildInfo.get("paramType").equals(DataType.Array)) {
                buildInfo.put("items", info.get(ParamKey.ITEMS) != null ? info.get(ParamKey.ITEMS) : new HashMap<>());
                buildInfo.put("maxItems", info.getOrDefault(ParamKey.MAXITEMS, 3));
                buildInfo.put("minItems", info.getOrDefault(ParamKey.MINITEMS, 1));
                buildInfo.put("unique", info.getOrDefault(ParamKey.UNIQUEITEMS, false));
                return ArrayParam.buildArray(buildInfo, definitions);
            } else if (buildInfo.get("paramType") == DataType.Bool) {
                return new BoolParam(
                        (String) buildInfo.get("specifiedName"),
                        (List<Object>) buildInfo.get("default"),
                        (Loc) buildInfo.get("loc"),
                        (boolean) buildInfo.get("required"),
                        (DataType) buildInfo.get("paramType"),
                        (DataType) buildInfo.get("paramFormat"),
                        (String) buildInfo.get("description")
                );
            } else if (buildInfo.get("paramType") == DataType.String) {
                if (Arrays.asList(DataType.NULL, DataType.Binary, DataType.Byte).contains(buildInfo.get("paramFormat"))) {
                    buildInfo.put("maxLength", info.getOrDefault(ParamKey.MAXLENGTH, 20));
                    buildInfo.put("minLength", info.getOrDefault(ParamKey.MINLENGTH, 1));
                    return new StringParam(
                            (String) buildInfo.get("specifiedName"),
                            (List<Object>) buildInfo.get("default"),
                            (Loc) buildInfo.get("loc"),
                            (boolean) buildInfo.get("required"),
                            (DataType) buildInfo.get("paramType"),
                            (DataType) buildInfo.get("paramFormat"),
                            (String) buildInfo.get("description"),
                            (int) buildInfo.get("maxLength"),
                            (int) buildInfo.get("minLength")
                    );
                } else if (buildInfo.get("paramFormat") == DataType.Date) {
                    return new Date(
                            (String) buildInfo.get("specifiedName"),
                            (List<Object>) buildInfo.get("default"),
                            (Loc) buildInfo.get("loc"),
                            (boolean) buildInfo.get("required"),
                            (DataType) buildInfo.get("paramType"),
                            (DataType) buildInfo.get("paramFormat"),
                            (String) buildInfo.get("description")
                    );
                } else if (buildInfo.get("paramFormat") == DataType.DateTime) {
                    return new DateTime(
                            (String) buildInfo.get("specifiedName"),
                            (List<Object>) buildInfo.get("default"),
                            (Loc) buildInfo.get("loc"),
                            (boolean) buildInfo.get("required"),
                            (DataType) buildInfo.get("paramType"),
                            (DataType) buildInfo.get("paramFormat"),
                            (String) buildInfo.get("description")
                    );
                } else if (buildInfo.get("paramFormat") == DataType.File) {
                    return new FileParam(
                            (String) buildInfo.get("specifiedName"),
                            (List<Object>) buildInfo.get("default"),
                            (Loc) buildInfo.get("loc"),
                            (boolean) buildInfo.get("required"),
                            (DataType) buildInfo.get("paramType"),
                            (DataType) buildInfo.get("paramFormat"),
                            (String) buildInfo.get("description")
                    );
                } else if (buildInfo.get("paramFormat") == DataType.UUID) {
                    return new UuidParam(
                            (String) buildInfo.get("specifiedName"),
                            (List<Object>) buildInfo.get("default"),
                            (Loc) buildInfo.get("loc"),
                            (boolean) buildInfo.get("required"),
                            (DataType) buildInfo.get("paramType"),
                            (DataType) buildInfo.get("paramFormat"),
                            (String) buildInfo.get("description")
                    );
                } else {
                    throw new UnsupportedError(info.toString() + " is not taken into consideration");
                }
            } else if (buildInfo.get("paramType") == DataType.File) {
                return new FileParam(
                        (String) buildInfo.get("specifiedName"),
                        (List<Object>) buildInfo.get("default"),
                        (Loc) buildInfo.get("loc"),
                        (boolean) buildInfo.get("required"),
                        (DataType) buildInfo.get("paramType"),
                        (DataType) buildInfo.get("paramFormat"),
                        (String) buildInfo.get("description")
                );
            } else if (buildInfo.get("paramType") == DataType.Object) {
                buildInfo.put("allInfo", info);
                buildInfo.put("required", info.getOrDefault("required", false));
//                System.out.println("info：" + info.get("required"));

                return ObjectParam.buildObject(buildInfo, definitions);
            } else {
                throw new UnsupportedError(info.toString() + " is not taken into consideration");
            }
    }

}

