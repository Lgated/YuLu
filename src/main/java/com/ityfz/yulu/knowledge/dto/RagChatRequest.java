package com.ityfz.yulu.knowledge.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * RAG 对话请求体
 */
@Data
public class RagChatRequest {

    /**
     * 用户问题（必填）
     */
    @NotBlank(message = "问题不能为空")
    private String question;

    /**
     * 检索条数，默认 8
     */
    private Integer topK = 8;

    /**
     * 相似度最低分数，低于该值的结果丢弃，默认 0.55
     */
    private Double minScore = 0.55;
}

