package com.kzip.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai/health")
public class AiHealthController {

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Value("${chromadb.host:127.0.0.1}")
    private String chromadbHost;

    @Value("${chromadb.port:8000}")
    private int chromadbPort;

    @GetMapping("/status")
    public Map<String, Object> healthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        status.put("service", "ai-module");
        status.put("version", "1.0.0");

        Map<String, Object> components = new HashMap<>();

        components.put("vectorStore", checkVectorStore());
        components.put("chromaDB", checkChromaDB());

        status.put("components", components);

        boolean allHealthy = components.values().stream()
                .allMatch(m -> "UP".equals(((Map<?, ?>) m).get("status")));
        status.put("status", allHealthy ? "UP" : "DEGRADED");

        return status;
    }

    private Map<String, Object> checkVectorStore() {
        Map<String, Object> result = new HashMap<>();

        if (vectorStore == null) {
            result.put("status", "DOWN");
            result.put("message", "VectorStore未注入，请检查ChromaDB配置");
            return result;
        }

        try {
            long startTime = System.currentTimeMillis();
            vectorStore.similaritySearch(SearchRequest.builder()
                    .query("health_check")
                    .topK(1)
                    .build());
            long duration = System.currentTimeMillis() - startTime;

            result.put("status", "UP");
            result.put("message", "VectorStore正常工作");
            result.put("responseTime", duration + "ms");
            result.put("type", vectorStore.getClass().getSimpleName());
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("message", "VectorStore查询失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> checkChromaDB() {
        Map<String, Object> result = new HashMap<>();
        result.put("host", chromadbHost);
        result.put("port", chromadbPort);
        result.put("url", "http://" + chromadbHost + ":" + chromadbPort);

        try {
            java.net.URL url = new java.net.URL("http://" + chromadbHost + ":" + chromadbPort + "/api/v1/heartbeat");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                result.put("status", "UP");
                result.put("message", "ChromaDB服务正常运行");
                result.put("httpStatus", responseCode);
            } else {
                result.put("status", "DOWN");
                result.put("message", "ChromaDB返回异常状态码: " + responseCode);
                result.put("httpStatus", responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("message", "无法连接到ChromaDB: " + e.getMessage());
            result.put("suggestion", "请运行: docker run -p 8000:8000 chromadb/chroma:latest");
        }

        return result;
    }
}