package com.ityfz.yulu.common.ai;

import java.util.List;

/**
 * 抽象大模型客户端接口，便于后续接 OpenAI / 通义千问 / 文心等。
 */
public interface LLMClient {

    /**
     * 带上下文的对话接口。
     *
     * @param context 最近几轮对话（user + assistant）
     * @param question 本轮用户输入的问题
     * @return 模型生成的回答
     */
    String chat(List<Message> context,String question);

    /**
     * 意图识别（退货 / 开发票 / 投诉 / 普通咨询 等）。
     */
    String detectIntent(String text);

    /**
     * 情绪识别（NORMAL / NEGATIVE / POSITIVE）。
     */
    String detectEmotion(String text);
}
