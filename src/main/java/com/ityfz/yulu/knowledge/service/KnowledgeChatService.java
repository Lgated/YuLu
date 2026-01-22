package com.ityfz.yulu.knowledge.service;

import com.ityfz.yulu.knowledge.dto.RagAugmentResult;
import com.ityfz.yulu.knowledge.dto.RagChatRequest;
import com.ityfz.yulu.knowledge.dto.RagChatResponse;

/**
 * 知识库 RAG 对话服务：检索 + 上下文构造 + LLM 生成
 */
public interface KnowledgeChatService {

    /**
     * 基于知识库的 RAG 对话（独立 RAG 接口，无对话历史）
     *
     * @param tenantId 租户 ID
     * @param request  请求（问题、topK、minScore 等）
     * @return 回答 + 引用列表
     */
    RagChatResponse ragChat(Long tenantId, RagChatRequest request);

    /**
     * 仅做 RAG 增强：检索 → 拼装「参考资料 + 用户问题」→ 返回增强后的 user 消息及 refs。
     * 供客服对话（ChatServiceImpl）在调用 LLM 前使用；不调 LLM。
     *
     * @param tenantId 租户 ID
     * @param question 用户问题
     * @return 增强后的本轮 user 消息（无检索时即 question）+ 引用列表
     */
    RagAugmentResult buildRagAugment(Long tenantId, String question);
}
