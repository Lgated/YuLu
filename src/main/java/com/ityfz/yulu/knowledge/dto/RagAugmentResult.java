package com.ityfz.yulu.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 增强结果：用于把「检索资料 + 用户问题」拼成发给 LLM 的本轮 user 消息，及引用列表。
 * 由 KnowledgeChatService.buildRagAugment 返回，供 ChatServiceImpl 在调用 LLM 前使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAugmentResult {

    /**
     * 发给 LLM 的本轮 user 消息：无检索时为原始 question；有检索时为「参考资料 + 用户问题」。
     */
    private String augmentedUserMessage;

    /**
     * 本轮检索到的引用，可随回答一并返回前端展示。
     */
    private List<RagRefDTO> refs;
}






















