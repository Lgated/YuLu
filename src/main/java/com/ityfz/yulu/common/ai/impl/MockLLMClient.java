package com.ityfz.yulu.common.ai.impl;

import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock 实现：基于关键字的简单规则，用来跑通业务链路。
 */
@Service("mockLLMClient")
public class MockLLMClient implements LLMClient {


    @Override
    public String chat(List<Message> context, String question) {
        // 简单示例：根据关键词返回固定回答
        String lower = question == null ? "" : question.toLowerCase();

        if (lower.contains("退款") || lower.contains("退货")) {
            return "我已经为你查询到订单，接下来会为你处理退款相关问题，请提供订单号。";
        }
        if (lower.contains("发票") || lower.contains("invoice")) {
            return "你可以在订单详情页自行下载电子发票，如需纸质发票请联系客服。";
        }
        if (lower.contains("投诉") || lower.contains("差评")) {
            return "非常抱歉给你带来不好的体验，我会为你升级为人工客服进一步处理。";
        }

        // 默认回答
        return "这是一个 Mock AI 回答，目前我只能根据部分关键词给出示例回复。";
    }

    @Override
    public String detectIntent(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("退款") || lower.contains("退货")) {
            return "REFUND";
        }
        if (lower.contains("发票") || lower.contains("invoice")) {
            return "INVOICE";
        }
        if (lower.contains("投诉") || lower.contains("差评") || lower.contains("垃圾")) {
            return "COMPLAIN";
        }
        return "GENERAL";
    }

    @Override
    public String detectEmotion(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("投诉") || lower.contains("差评") || lower.contains("垃圾") || lower.contains("气死")) {
            return "NEGATIVE";
        }
        // 简化版：没有明显负向，就当 NORMAL
        return "NORMAL";
    }
}
