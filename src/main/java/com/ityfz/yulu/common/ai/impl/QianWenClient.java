package com.ityfz.yulu.common.ai.impl;

import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.config.QianWenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("qianWenClient") // 这个名字后面会在 @Qualifier 里用到
public class QianWenClient implements LLMClient {
    private final QianWenProperties props;
    private final RestTemplate restTemplate;

    public QianWenClient(QianWenProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 多轮对话：把 Redis 里构造好的 messages 直接传给通义千问
     */
    @Override
    public String chat(List<Message> contextMessages, String question) {
        long start = System.currentTimeMillis();

        //1、组装messages（在原有上下文后再追加当前用户问题）
        // 如果ChatServiceImpl已经把当前问题追加到contextMessages了，这里就不需要了。
        Map<String,Object> input = new HashMap<>();
        input.put("messages",contextMessages);
        //参数设置
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("result_format", "message");

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        //2、Header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey()); // = Authorization: Bearer xxx

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // 判断是兼容模式还是标准模式
        String baseUrl = props.getBaseUrl();
        String url;
        boolean isCompatibleMode = baseUrl.contains("/compatible");
        
        if (isCompatibleMode) {
            // 兼容模式：使用 OpenAI 兼容接口
            // baseUrl 应该是 https://dashscope.aliyuncs.com/compatible/v1
            // 拼接后：https://dashscope.aliyuncs.com/compatible/v1/chat/completions
            url = baseUrl + "/chat/completions";
            // 兼容模式的请求体格式不同（OpenAI 格式）
            body = new HashMap<>();
            body.put("model", props.getModel());
            body.put("messages", contextMessages);
            // 兼容模式使用 Authorization: Bearer {api_key}
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());
            request = new HttpEntity<>(body, headers);
            log.info("[QianWen] 使用兼容模式，URL: {}, Model: {}, Messages: {}", url, props.getModel(), contextMessages.size());
        } else {
            // 标准模式：使用通义千问原生接口
            url = baseUrl + "/services/aigc/text-generation/generation";
            // 标准模式使用 X-DashScope-API-Key 头
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-DashScope-API-Key", props.getApiKey());
            request = new HttpEntity<>(body, headers);
            log.info("[QianWen] 使用标准模式，URL: {}, Model: {}", url, props.getModel());
        }

        try {
            //1、调用通义千问
            log.debug("[QianWen] 请求URL: {}, 请求体: {}", url, body);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("调用通义千问失败，status={}, body={}", resp.getStatusCode(), resp.getBody());
                return "抱歉，我暂时无法回答你的问题，请稍后再试。";
            }

            Map<String, Object> respBody = resp.getBody();
            String content;
            
            if (isCompatibleMode) {
                // 兼容模式响应格式：choices[0].message.content
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
                if (choices == null || choices.isEmpty()) {
                    log.warn("通义千问返回 choices 为空: {}", respBody);
                    return "抱歉，我暂时没有合适的回答。";
                }
                Map<String, Object> first = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) first.get("message");
                content = (String) message.get("content");
            } else {
                // 标准模式响应格式：output.choices[0].message.content
                Map<String, Object> output = (Map<String, Object>) respBody.get("output");
                if (output == null) {
                    log.warn("通义千问返回 output 为空: {}", respBody);
                    return "抱歉，我暂时没有合适的回答。";
                }
                List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
                if (choices == null || choices.isEmpty()) {
                    log.warn("通义千问返回 choices 为空: {}", respBody);
                    return "抱歉，我暂时没有合适的回答。";
                }
                Map<String, Object> first = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) first.get("message");
                content = (String) message.get("content");
            }

            long cost = System.currentTimeMillis() - start;
            log.info("[QianWen] 调用成功, model={}, questionLen={}, cost={}ms",
                    props.getModel(), question != null ? question.length() : 0, cost);

            return content;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // HTTP 错误（4xx, 5xx）
            String errorBody = e.getResponseBodyAsString();
            log.error("[QianWen] HTTP错误: status={}, body={}, URL={}", 
                    e.getStatusCode(), errorBody, url, e);
            
            // 如果是 404，可能是 URL 或 API Key 问题
            if (e.getStatusCode().value() == 404) {
                return "通义千问 API 地址错误或 API Key 无效，请检查配置。错误详情：" + 
                       (errorBody != null && !errorBody.isEmpty() ? errorBody : e.getMessage());
            }
            // 如果是 401，是认证问题
            if (e.getStatusCode().value() == 401) {
                return "通义千问 API Key 无效或已过期，请检查配置。";
            }
            
            return "调用通义千问出错：" + e.getStatusCode() + " - " + 
                   (errorBody != null && !errorBody.isEmpty() ? errorBody : e.getMessage());
        } catch (Exception e) {
            log.error("[QianWen] 调用异常: URL={}", url, e);
            return "调用通义千问出错：" + e.getMessage();
        }
    }

    @Override
    public String detectIntent(String text) {
        return null;
    }

    /**
     * 情绪识别：可以先用简单规则 / mock，后面再接入真正的情绪模型
     */
    @Override
    public String detectEmotion(String text) {
        if (text == null) {
            return "NEUTRAL";
        }
        String t = text.toLowerCase();
        if (t.contains("退货") || t.contains("投诉") || t.contains("生气")) {
            return "ANGRY";
        }
        if (t.contains("谢谢") || t.contains("感激")) {
            return "HAPPY";
        }
        return "NEUTRAL";
    }
}
