package com.ityfz.yulu.common.ai.impl;


import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.config.QianWenProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

        // 可选：system 提示词（建议先固定一个最小版）
        messages.add(SystemMessage.from("你是一个专业的客服助手，回答要简洁、准确。"));


        // 把 Redis 上下文（role/content）映射为 LangChain4j 消息
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

        // 再追加本轮用户问题
        messages.add(UserMessage.from(question));

        // 调用模型
        return model.chat(messages).aiMessage().text();
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
}
