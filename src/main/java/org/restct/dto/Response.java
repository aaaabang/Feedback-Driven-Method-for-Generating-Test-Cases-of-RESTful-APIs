package org.restct.dto;

import org.restct.dto.keywords.DocKey;
import org.restct.dto.keywords.ParamKey;
import org.restct.dto.param.AbstractParam;
import org.restct.dto.param.Parameter;
import org.restct.exception.UnsupportedError;

import java.util.Map;

public class Response {

    private String expected_status_code;
    private AbstractParam template;
    private Operation operation;

    public Response(String statusCode, AbstractParam tmp, Operation op){
        this.expected_status_code = statusCode;
        this.template = tmp;
        this.operation = op;
    }

    public static Response buildResponse(String statusCode, Map<String, Object> responseInfo, Map<String, Map<String, Object>> definitions, Operation operation) throws UnsupportedError {

        Object schema = responseInfo.get(ParamKey.SCHEMA);
        if (schema == null) {
            // status_code: 204, response_info: {'description': 'Deletes a project'}
            return new Response(statusCode, null, operation);
        } else {
            Object ref_info = ((Map) schema).get(DocKey.REF_SIGN);
            String s_type = (String) ((Map) schema).get(ParamKey.TYPE);
            if (ref_info != null) {
                Map<String, Object> ref = AbstractParam.getRef((String) ref_info, definitions);
                AbstractParam content = Parameter.buildParam(ref, definitions, null);
                return new Response(statusCode, content, operation);
            } else if (s_type != null) {
                AbstractParam content = Parameter.buildParam((Map<String, Object>) schema, definitions, null);
                return new Response(statusCode, content, operation);
            } else {
                throw new UnsupportedError(responseInfo + " can not be transferred to Response");
            }
        }
    }
}
