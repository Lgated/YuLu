package com.ityfz.yulu.common.ai.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.config.QianWenProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component("langChain4jQwenClient")
public class LangChain4jQwenClient implements LLMClient {

    private final OpenAiChatModel model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 可选：缓存最近一次结果，后面 detectEmotion/detectIntent 可以复用
    private final ThreadLocal<ChatResult> lastResult = new ThreadLocal<>();

    @Data
    // 内部使用的结构化结果
    static class ChatResult {
        private String answer;
        private String emotion;
        private String intent;
    }


    public LangChain4jQwenClient(QianWenProperties props) {
        // 关键：OpenAiChatModel 可以指定 baseUrl，接入 DashScope 的 OpenAI 兼容接口
        this.model = OpenAiChatModel.builder()
                .baseUrl(props.getBaseUrl()) // 例如 https://dashscope.aliyuncs.com/compatible/v1
                .apiKey(props.getApiKey())   // Bearer Token
                .modelName(props.getModel()) // 例如 qwen-turbo
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public String chat(List<Message> context, String question) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1）System 提示词：要求模型输出 JSON
        messages.add(SystemMessage.from(
                "你是一个专业的客服助手，所有输出必须是 JSON：" +
                        "{ \"answer\": \"...\", \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL|NORMAL\", " +
                        "\"intent\": \"REFUND|INVOICE|COMPLAIN|GENERAL\" }。" +
                        "answer 是最终给用户看的自然语言回答；" +
                        "emotion 是情绪标签；intent 是用户意图标签。" +
                        "不要输出任何说明文字，不要输出 JSON 以外的内容。"
        ));


        // 2）把历史上下文从 List<Message> 转成 LangChain4j 的 ChatMessage
        if (context != null) {
            List<Message> sortedContext = new ArrayList<>(context);
            Collections.reverse(sortedContext);
            for (Message m : context) {
                if (m == null) continue;
                String role = m.getRole();
                String content = m.getContent();
                if (content == null) content = "";
                if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
                    messages.add(AiMessage.from(content));
                } else {
                    messages.add(UserMessage.from(content));
                }
            }
        }

        // 3）再追加本轮用户问题
        messages.add(UserMessage.from(question));


        // 4）调用模型
        ChatResponse response = model.chat(messages);
        String rawText = response.aiMessage().text();

        // 5）解析 JSON 得到结构化结果
        ChatResult result = parseChatResult(rawText);
        lastResult.set(result); // 可选：缓存，后续可以利用 lastResult 优化情绪 / 意图


        // 6）对外仍然只返回 answer 文本
        return result.getAnswer();
    }

    @Override
    public String detectIntent(String text) {
        // 第一期先不做：保持与你现有接口一致
        return null;
    }

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

    // 解析 JSON 的工具方法
    private ChatResult parseChatResult(String rawText) {
        ChatResult result = new ChatResult();
        try {
            JsonNode root = objectMapper.readTree(rawText);
            // answer
            String answer = root.path("answer").asText(null);
            if (answer == null || answer.isEmpty()) {
                // 如果模型没按规矩返回 answer，就把整段文本当成 answer
                answer = rawText;
            }
            result.setAnswer(answer);

            // emotion
            String emotion = root.path("emotion").asText("NORMAL");
            result.setEmotion(emotion.toUpperCase());

            // intent
            String intent = root.path("intent").asText("GENERAL");
            result.setIntent(intent.toUpperCase());
        } catch (Exception e) {
            // 解析失败：退化为“纯文本回答 + 默认标签”
            log.warn("[LangChain4jQwenClient] 解析 JSON 失败，使用原始文本作为 answer，text={}", rawText, e);
            result.setAnswer(rawText);
            result.setEmotion("NORMAL");
            result.setIntent("GENERAL");
        }
        return result;
    }

}
