package com.ityfz.yulu.common.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.common.ai.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope text-embedding-v2 实现
 * 直接调用 DashScope Embedding API
 */
@Slf4j
@Service
public class DashScopeEmbeddingService implements EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.embedding.api-key:${ai.qianwen.api-key}}")
    private String apiKey;

    @Value("${ai.embedding.base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String baseUrl;

    @Value("${ai.embedding.model:text-embedding-v2}")
    private String model;

    @Value("${ai.embedding.dimension:1536}")
    private int dimension;

    @Autowired
    public DashScopeEmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Float> embed(String text) {
        // trim(): 把字符串首尾的所有空白字符（空格、Tab、换行、全角空格等）去掉，返回一个新的字符串。
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }

        try {
            // 构建请求 URL
            String url = baseUrl + "/services/embeddings/text-embedding/text-embedding";

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("[DashScope Embedding] 请求: model={}, text={}", model, text);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class
            );

            // 解析响应
            JsonNode root = objectMapper.readTree(response.getBody());

            // DashScope 响应格式：{"output":{"embeddings":[{"embedding":[...]}]}}
            if (root.has("output") && root.get("output").has("embeddings")) {
                JsonNode embeddings = root.get("output").get("embeddings");
                if (embeddings.isArray() && embeddings.size() > 0) {
                    JsonNode embedding = embeddings.get(0);
                    if (embedding.has("embedding")) {
                        JsonNode embeddingArray = embedding.get("embedding");
                        List<Float> vector = new ArrayList<>();
                        for (JsonNode value : embeddingArray) {
                            vector.add(value.floatValue());
                        }
                        log.debug("[DashScope Embedding] 成功: dimension={}", vector.size());
                        return vector;
                    }
                }
            }

            // 兼容其他可能的响应格式
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode data = root.get("data").get(0);
                if (data.has("embedding")) {
                    JsonNode embeddingArray = data.get("embedding");
                    List<Float> vector = new ArrayList<>();
                    for (JsonNode value : embeddingArray) {
                        vector.add(value.floatValue());
                    }
                    log.debug("[DashScope Embedding] 成功（兼容格式）: dimension={}", vector.size());
                    return vector;
                }
            }

            throw new RuntimeException("无法解析 Embedding 响应: " + response.getBody());

        } catch (Exception e) {
            log.error("[DashScope Embedding] 调用失败: text={}", text, e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        // DashScope 支持批量，最多 25 条
        int batchSize = 25;
        List<List<Float>> results = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                // 构建请求 URL
                String url = baseUrl + "/services/embeddings/text-embedding/text-embedding";

                // 构建请求头
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("Content-Type", "application/json");

                // 构建请求体（批量）
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("input", batch);  // 批量输入

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

                log.debug("[DashScope Embedding] 批量请求: model={}, batchSize={}", model, batch.size());

                // 发送请求
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, request, String.class
                );

                // 解析响应
                JsonNode root = objectMapper.readTree(response.getBody());

                // DashScope 批量响应格式：{"output":{"embeddings":[{"embedding":[...]}, ...]}}
                if (root.has("output") && root.get("output").has("embeddings")) {
                    JsonNode embeddings = root.get("output").get("embeddings");
                    for (JsonNode embedding : embeddings) {
                        if (embedding.has("embedding")) {
                            JsonNode embeddingArray = embedding.get("embedding");
                            List<Float> vector = new ArrayList<>();
                            for (JsonNode value : embeddingArray) {
                                vector.add(value.floatValue());
                            }
                            results.add(vector);
                        }
                    }
                } else {
                    // 如果批量失败，逐个调用
                    log.warn("[DashScope Embedding] 批量响应格式异常，转为逐个调用");
                    for (String text : batch) {
                        results.add(embed(text));
                    }
                }

            } catch (Exception e) {
                log.error("[DashScope Embedding] 批量调用失败: batch={}", batch, e);
                // 如果批量失败，逐个调用
                for (String text : batch) {
                    try {
                        results.add(embed(text));
                    } catch (Exception ex) {
                        log.error("[DashScope Embedding] 单个文本调用也失败: text={}", text, ex);
                        // 添加空向量或抛出异常，根据业务需求决定
                        results.add(new ArrayList<>());
                    }
                }
            }
        }

        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}
