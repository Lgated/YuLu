package com.ityfz.yulu.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatResponse {

    /**
     * 模型生成的回答
     */
    private String answer;

    /**
     * 本次检索并注入的引用来源（供前端展示“依据”）
     */
    private List<RagRefDTO> refs;
}

