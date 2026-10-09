package com.example.demo.Bank;

import com.alibaba.fastjson.JSONObject;
import com.wwfinance.common.utils.HttpUtils;
import com.wwfinance.common.utils.MD5;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class RequestHelper {
    /**
     * 请求数据获取签名y
     * @param paramMap
     * @return
     */
    public static String getSign(Map<String, Object> paramMap) {
        if(paramMap.containsKey("sign")) {
            paramMap.remove("sign");
        }
        TreeMap<String, Object> sorted = new TreeMap<>(paramMap);
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, Object> param : sorted.entrySet()) {
            str.append(param.getValue()).append("|");
            
        }
        str.append(HfbConst.SIGN_KEY);
        log.info("加密前：" + str.toString());
        String md5Str = MD5.encrypt(str.toString());
        log.info("加密后：" + md5Str);
        return md5Str;
    }

    /**
     * Map转换
     * @param paramMap
     * @return
     */
    public static Map<String, Object> switchMap(Map<String, String[]> paramMap) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
            resultMap.put(param.getKey(), param.getValue()[0]);
        }
        return resultMap;
    }

    /**
     *  签名校验
     * @param paramMap
     * @return
     */
    public static boolean isSignEquals(Map<String, Object> paramMap) {
        String sign = (String)paramMap.get("sign");
        String md5Str = getSign(paramMap);
        if(!sign.equals(md5Str)) {
            return false;
        }
        return true;
    }

    /**
     * 获取时间戳
     * @return
     */
    public static long getTimestamp() {
        return new Date().getTime();
    }

    /**
     * 封装同步请求
     * @param paramMap
     * @param url
     * @return
     */
    public static JSONObject sendRequest(Map<String, Object> paramMap, String url){
        String result = "";
        try {
            //封装post参数
            StringBuilder postdata = new StringBuilder();
            for (Map.Entry<String, Object> param : paramMap.entrySet()) {
                postdata.append(param.getKey()).append("=")
                        .append(param.getValue()).append("&");
            }
            log.info(String.format("--> 发送请求到旺旺银行：post data %1s", postdata));
            byte[] reqData = postdata.toString().getBytes("utf-8");
            byte[] respdata = HttpUtils.doPost(url,reqData);
            result = new String(respdata);
            log.info(String.format("--> 旺旺银行应答结果：result data %1s", result));
        } catch (Exception ex) {
            log.error("请求旺旺银行失败", ex);
        }
        return JSONObject.parseObject(result);
    }

    /**
     * 封装同步请求（表单编码版）。
     *
     * <p><b>为什么要另写一个：</b>{@link #sendRequest(Map, String)} 是把参数裸拼成
     * {@code k=v&k=v&} 后直接写字节，既不 URL 编码也不声明 Content-Type。
     * 放款/还款参数里带中文（标的标题 agentProjectName），裸拼会产生
     * ①非 ASCII 字节被 Servlet 解析成乱码；②Tomcat 在缺少
     * {@code application/x-www-form-urlencoded} 时不解析请求体，
     * 银行侧 {@code request.getParameterMap()} 拿不到参数 → 验签直接失败。
     *
     * @param paramMap 请求参数（值可为任意类型，按 String 处理）
     * @param url      银行接口地址
     * @return 银行应答 JSON；无应答或不是 JSON 时返回 null
     */
    public static JSONObject sendFormRequest(Map<String, Object> paramMap, String url) {
        StringBuilder postdata = new StringBuilder();
        for (Map.Entry<String, Object> param : paramMap.entrySet()) {
            if (postdata.length() > 0) {
                postdata.append('&');
            }
            Object v = param.getValue();
            postdata.append(urlEncode(param.getKey()))
                    .append('=')
                    .append(urlEncode(v == null ? "" : String.valueOf(v)));
        }
        log.info("--> 发送请求到旺旺银行（表单编码）：url={}, data={}", url, postdata);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");

            byte[] body = postdata.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
            }
            String result = sb.toString();
            log.info("--> 旺旺银行应答结果：http={}, body={}", code, result);

            if (result.isEmpty()) {
                return null;
            }
            return JSONObject.parseObject(result);
        } catch (Exception ex) {
            log.error("请求旺旺银行失败，url={}", url, ex);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }


    public static void main(String[] args) {
        Map map = new HashMap();
        map.put("name","zs");
        map.put("age",20);
        String sign = RequestHelper.getSign(map);
    }
}
