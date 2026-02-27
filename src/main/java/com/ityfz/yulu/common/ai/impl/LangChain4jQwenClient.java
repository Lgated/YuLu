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

        // 1）System 提示词：要求模型输出 JSON；若用户消息中含【参考资料】，请结合资料作答
        messages.add(SystemMessage.from(
                "你是一个专业的客服助手，所有输出必须是 JSON：" +
                        "{ \"answer\": \"...\", \"emotion\": \"HAPPY|ANGRY|SAD|NEUTRAL|NORMAL\", " +
                        "\"intent\": \"REFUND|INVOICE|COMPLAIN|GENERAL\" }。" +
                        "answer 是最终给用户看的自然语言回答；emotion 是情绪标签；intent 是用户意图标签。" +
                        "当用户消息中包含【参考资料】时，请优先结合这些资料作答，并保持客服语气；否则按常规客服方式回复。" +
                        "不要输出任何说明文字，不要输出 JSON 以外的内容。"
        ));


        // 2）把历史上下文从 List<Message> 转成 LangChain4j 的 ChatMessage
        if (context != null) {
            List<Message> sortedContext = new ArrayList<>(context);
            Collections.reverse(sortedContext);

            // ✅ 添加调试日志：打印上下文
            log.info("========== [AI上下文调试] ==========");
            log.info("历史上下文数量: {}", sortedContext.size());
            for (int i = 0; i < sortedContext.size(); i++) {
                Message m = sortedContext.get(i);
                log.info("  [{}] role={}, content={}", i, m.getRole(),
                        m.getContent() != null && m.getContent().length() > 50
                                ? m.getContent().substring(0, 50) + "..."
                                : m.getContent());
            }
            log.info("当前问题: {}", question);
            log.info("====================================");

            for (Message m : sortedContext) {
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
        if (text == null || text.isBlank()) return "GENERAL";
        try {
            List<ChatMessage> msgs = new ArrayList<>();
            msgs.add(SystemMessage.from(
                    "你是意图识别助手。仅返回JSON：{ \"intent\": \"REFUND|INVOICE|LOGISTICS|COMPLAIN|GENERAL\" }，不要输出其他文字。"
            ));
            msgs.add(UserMessage.from(text));

            String json = model.chat(msgs).aiMessage().text();
            if (!isValidJson(json)) {
                return fallbackRuleIntent(text);
            }

            JsonNode node = objectMapper.readTree(json);
            String intent = node.path("intent").asText("GENERAL").toUpperCase();
            return normalizeIntent(intent);
        } catch (Exception e) {
            log.warn("[LLM] 意图识别失败，回退规则。text={}", text, e);
            return fallbackRuleIntent(text);
        }
    }

    //TODO:和  chat（） 共享一次调用结果
    @Override
    public String detectEmotion(String text) {

        if (text == null || text.isEmpty()) {
            return "NORMAL";
        }

        try {
            // 新建一个空列表，用来存放一次对话里的所有消息
            List<ChatMessage> msgs = new ArrayList<>();
            // 给模型设定“全局行为准则”
            msgs.add(SystemMessage.from(
                    "你是一个情绪分析助手，请根据用户这句话判断情绪。" +
                            "只返回 JSON：{ \"emotion\": \"HAPPY|ANGRY|NEUTRAL\" }，不要输出其他任何文字。"
            ));
            msgs.add(UserMessage.from(text));

            String json = model.chat(msgs).aiMessage().text();
            // 先判断是否是有效JSON
            if (!isValidJson(json)) {
                log.debug("[LLM] 模型返回非JSON格式，回退到规则实现。text={}, response={}", text, json);
                return fallbackRuleEmotion(text);
            }


            ObjectMapper mapper = new ObjectMapper();
            // 把上一步的字符串解析成 树形 JSON 节点，方便按路径取值
            JsonNode node = mapper.readTree(json);
            String emotion = node.path("emotion").asText("NEUTRAL").toUpperCase();
            return emotion;

        } catch (Exception e) {
            log.warn("[LLM] 情绪识别失败，回退到规则实现。text={}", text, e);
            // 回退为你原来那套关键字规则，确保不会影响主流程
            return fallbackRuleEmotion(text);
        }
    }

    /**
     * 使用简单关键字规则
     */
    private String fallbackRuleEmotion(String text) {
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

        // 先判断是否是有效JSON
        if (!isValidJson(rawText)) {
            log.debug("[LangChain4jQwenClient] 模型返回非JSON格式，使用原始文本作为 answer，text={}", rawText);
            result.setAnswer(rawText);
            result.setEmotion("NORMAL");
            result.setIntent("GENERAL");
            return result;
        }

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

    /**
     * 判断字符串是否是有效的 JSON 格式
     *
     * @param text 待判断的文本
     * @return true 如果是有效JSON，false 否则
     */
    private boolean isValidJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        try {
            objectMapper.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String fallbackRuleIntent(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("退货") || t.contains("退款")) return "REFUND";
        if (t.contains("发票")) return "INVOICE";
        if (t.contains("物流") || t.contains("快递") || t.contains("发货")) return "LOGISTICS";
        if (t.contains("投诉") || t.contains("差评") || t.contains("生气")) return "COMPLAIN";
        return "GENERAL";
    }

    private String normalizeIntent(String intent) {
        switch (intent) {
            case "REFUND":
            case "INVOICE":
            case "LOGISTICS":
            case "COMPLAIN":
            case "GENERAL":
                return intent;
            default:
                return "GENERAL";
        }
    }
}
