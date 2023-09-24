package org.restct.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import javafx.util.Pair;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.restct.Config;
import org.restct.RESTCT;
import org.restct.dto.Operation;
import org.restct.dto.keywords.DataType;
import org.restct.dto.keywords.Loc;
import org.restct.dto.keywords.ValueType;
import org.restct.dto.param.AbstractParam;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.restct.RESTCT.logger;

class Auth {
    private Map<String, Object> headerAuth;

    public Auth() {
        this.headerAuth = Config.header;
    }

    public void apply(Request.Builder builder) {
        headerAuth.forEach((k, v) -> {
            if (Objects.nonNull(k) && Objects.nonNull(v)) {
                builder.addHeader(k, (String) v);
            }
        });
    }

}

public class SendRequest {
    private static int callNumber = 0;
    private static int successNumber = 0;
    private Operation operation;
    private List<Map<String, Pair<ValueType, ?>>> coverArray;
    private Map<String, Object> responses;


    static private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build();

    public static Logger logger = LogManager.getLogger(RESTCT.class);

    static public Response delete(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(url);

        Auth auth = new Auth();
        auth.apply(requestBuilder);
        requestBuilder.delete();
        Response response = client.newCall(requestBuilder.build()).execute();
        return response;
    }

    public SendRequest(Operation operation, List<Map<String, Pair<ValueType, ?>>> coverArray, Map<String, Object> responses) {
        this.operation = operation;
        this.coverArray = coverArray;
        this.responses = responses;
    }

    public static int getcallNumber() {
        return callNumber;
    }

    public static int getSuccessNumber() {
        return successNumber;
    }

    public Pair<List<Integer>, List<Object>> run() throws Exception {
        List<Integer> statusCodes = new ArrayList<>();
        List<Object> responses = new ArrayList<>();

        for (Map<String, Pair<ValueType, ?>> caseItem : coverArray) {
            setParamValue(caseItem);
            Map<String, Object> kwargs = assemble();
            Pair<Integer, Object> result = send(kwargs);
            statusCodes.add(result.getKey());
            responses.add(result.getValue());
        }

        return new Pair<>(statusCodes, responses);
    }

    private Pair<Integer, Object> send(Map<String, Object> kwargs) throws Exception {
        SendRequest.callNumber++;


        Request.Builder requestBuilder = new Request.Builder();

        Map<String, String> headers = (Map<String, String>) kwargs.get("headers");
        // Set headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(kwargs.get("url").toString()).newBuilder();
        // Set query params
        Map<String, Object> params = (Map<String, Object>) kwargs.getOrDefault("params", new HashMap<>());
        try {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue().toString());
            }
        } catch (Exception e){
            System.out.println("{}" + e);
        }
        String url = urlBuilder.build().toString();
        requestBuilder.url(url);


        RequestBody body = RequestBody.create(null, new byte[0]);

        //set formdata
        Map<String, Object> formdata = (Map<String, Object>) kwargs.getOrDefault("data", new HashMap<>());
        FormBody.Builder formBody = new FormBody.Builder();
        for (Map.Entry<String, Object> entry: formdata.entrySet()) {
            formBody.add(entry.getKey(), entry.getValue().toString());
        }
        if (formdata.size() > 0) {
            body = formBody.build();
        }

        //set json 同时出现json和data参数，data会被覆盖，优先json
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Object json = kwargs.getOrDefault("json", null);

        if (json != null){
            // 使用Gson库将HashMap转换为JSON字符串
            Gson gson = new Gson();
            String jsonBody = gson.toJson(json);
            RequestBody jsonRequestBody = RequestBody.create(JSON, jsonBody);
            body = jsonRequestBody;
        }

        // 根据请求方法获取对应的函数
        String method = this.operation.method.getValue();
        if (method.equalsIgnoreCase("get")) {
            requestBuilder.get();
        } else if (method.equalsIgnoreCase("post")) {
            requestBuilder.post(body);
        } else if (method.equalsIgnoreCase("put")) {
            requestBuilder.put(body);
        } else if (method.equalsIgnoreCase("delete")) {
            requestBuilder.delete();
        } else {
            throw new Exception("request type error: " + method);
        }

        Response response;
        try {
            //set Auth Token
            Auth auth = new Auth();
            auth.apply(requestBuilder);


            // 发送请求
            response = client.newCall(requestBuilder.build()).execute();
//            if(response.code() < 300 && method.equalsIgnoreCase("post") ){
//                logger.debug("anxin");
//            }

        } catch (SocketTimeoutException e) {
            // 超时
            response = null;
        } catch (ProtocolException e) {
            // 重定向次数超5
            throw new Exception("bad url, try a different one\n url: " + kwargs.get("url"));
        } catch (IOException e) {
            // 其他异常处理
            response = null;
        }

        if (response == null) {
//            logger.error("response fail {} {}",response.code(), response.body().string() );

            return new Pair<>(600, null);
        } else {

            // 尝试解析响应内容
            int statusCode = 0;
            String responseData = null;
            try {
                responseData = response.body().string(); // 获取响应体
                statusCode = response.code();
                JSONObject jsonResponse = new JSONObject(responseData); // 尝试解析为JSON格式
                if (response.isSuccessful()){
                    successNumber++;
                }
//                else {
//                    logger.error("response fail {} {}",responseData, statusCode );
//                }
                response.close(); // 关闭响应体
                return new Pair<>(statusCode, jsonResponse);
            } catch (JSONException e) {
                // 解析JSON失败，处理为文本内容
                String textResponse = responseData;

                response.close(); // 关闭响应体
                return new Pair<>(statusCode, textResponse);
            }
        }

    }


    private void setParamValue(Map<String, Pair<ValueType, ?>> caseMap) {
        List<AbstractParam> parameters = this.operation.genDomain(new HashMap<>(), new HashMap<>());
        for (AbstractParam p : parameters) {
            p.value = (Pair<ValueType, Object>) caseMap.getOrDefault(p.name, null);
//            if (p.value == null)
//                logger.error("CA.setParamValue p.value is null.");
        }
    }

    private Map<String, Object> assemble() {
        String url = this.operation.url;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("user-agent", "my-app/0.0.1");
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> files = new HashMap<>();
        Map<String, Object> formData = new HashMap<>();
        Map<String, Object> body = new HashMap<>();

        for (AbstractParam p : this.operation.parameterList) {
            Object value = p.printableValue(this.responses);//TODO: 确认value除了是基本类型，只能是Map<String, Object>
            if (value == null) {
                if (p.getLoc() == Loc.Path) {
                    url = url.replace("{" + p.getName() + "}", "abc");
                }
            } else {
                if (p.getType() == DataType.File) {
                    files = (Map<String, Object>) value;
                } else if (p.getLoc() == Loc.Path) {
                    url = url.replace("{" + p.getName() + "}", value.toString());
                } else if (p.getLoc() == Loc.Query) {
                    params.put(p.getName(), value);
                } else if (p.getLoc() == Loc.Header) {
                    headers.put(p.getName(), value.toString());
                } else if (p.getLoc() == Loc.FormData) {
                    if (value instanceof Map) {
                        formData.putAll((Map<String, Object>) value);
                    } else {
                        formData.put(p.getName(), value);
                    }
                } else if (p.getLoc() == Loc.Body) {
                    if (value instanceof Map) {
                        body.putAll((Map<String, Object>) value);
                    } else {
                        body.put(p.getName(), value);
                    }
                } else {
                    throw new RuntimeException("Unexpected Param Loc Type: " + p.getName());
                }
            }
        }

        if (Config.query != null && !Config.query.isEmpty()) {
            params.putAll(Config.query);
        }

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("url", url);
        kwargs.put("headers", headers);

        if (params.size() > 0) {
            kwargs.put("params", params);
        }
        if (files.size() > 0) {
            kwargs.put("files", files);
        }
        if (formData.size() > 0) {
            kwargs.put("data", formData);
        }
        if (body.size() > 0) {
            kwargs.put("json", body);
        }
        return kwargs;


    }
}

