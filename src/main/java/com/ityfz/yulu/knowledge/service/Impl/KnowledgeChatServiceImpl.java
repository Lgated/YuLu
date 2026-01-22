package com.ityfz.yulu.knowledge.service.Impl;

import com.ityfz.yulu.common.ai.LLMClient;
import com.ityfz.yulu.common.ai.Message;
import com.ityfz.yulu.common.ai.impl.QdrantVectorStore;
import com.ityfz.yulu.knowledge.dto.RagAugmentResult;
import com.ityfz.yulu.knowledge.dto.RagChatRequest;
import com.ityfz.yulu.knowledge.dto.RagChatResponse;
import com.ityfz.yulu.knowledge.dto.RagRefDTO;
import com.ityfz.yulu.knowledge.dto.RetrievalResultDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ityfz.yulu.knowledge.entity.Chunk;
import com.ityfz.yulu.knowledge.mapper.ChunkMapper;
import com.ityfz.yulu.knowledge.service.KnowledgeChatService;
import com.ityfz.yulu.knowledge.service.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 RAG 对话实现：检索 → 上下文拼装 → Prompt 构造 → LLM 生成
 * 使用 QianWenClient（不强制 JSON），避免与客服场景的 LangChain4j 客户端冲突。
 */
@Service
@Slf4j
public class KnowledgeChatServiceImpl implements KnowledgeChatService {

    // 最大上下文字数
    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final int MAX_CHUNK_CHARS = 500;

    private final KnowledgeSearchService searchService;
    private final LLMClient llmClient;
    private final ChunkMapper chunkMapper;

    public KnowledgeChatServiceImpl(KnowledgeSearchService searchService,
                                    @Qualifier("langChain4jQwenClient") LLMClient llmClient,
                                    QdrantVectorStore qdrantVectorStore,
                                    ChunkMapper chunkMapper) {
        this.searchService = searchService;
        this.llmClient = llmClient;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public RagChatResponse ragChat(Long tenantId, RagChatRequest request) {
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isEmpty()) {
            return RagChatResponse.builder()
                    .answer("请输入问题。")
                    .refs(Collections.emptyList())
                    .build();
        }

        int topK = request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : 8;
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.55;

        // 1. 检索
        List<RetrievalResultDTO> hits = searchService.search(tenantId, question, topK, minScore);
        if (hits == null || hits.isEmpty()) {
            log.info("[RAG] 未检索到相关片段, tenantId={}, question={}", tenantId, question);
            return RagChatResponse.builder()
                    .answer("未在知识库找到与当前问题相关的内容，请换一种表述或补充业务范围后再试。")
                    .refs(Collections.emptyList())
                    .build();
        }

        // 2. 组装上下文
        String context = buildContext(hits, MAX_CONTEXT_CHARS);
        if (context.isEmpty()) {
            return RagChatResponse.builder()
                    .answer("未在知识库找到可用参考资料。")
                    .refs(toRefs(hits))
                    .build();
        }

        // 3. 构造发给 LLM 的完整用户消息（RAG 指令 + 资料 + 问题）
        String userMessage = buildRagUserMessage(question, context);

        // 4. 调用 LLM：QianWenClient 只使用 messages，不追加 question；此处用单条 user 消息即可
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", userMessage));

        String answer;
        try {
            answer = llmClient.chat(messages, "");
        } catch (Exception e) {
            log.error("[RAG] LLM 调用失败, tenantId={}, question={}", tenantId, question, e);
            answer = "回答生成失败，请稍后重试。";
        }

        return RagChatResponse.builder()
                .answer(answer)
                .refs(toRefs(hits))
                .build();
    }

    @Override
    public RagAugmentResult buildRagAugment(Long tenantId, String question) {

        if (question == null || question.trim().isEmpty()) {
            return RagAugmentResult.builder()
                    .augmentedUserMessage(question == null ? "" : question.trim())
                    .refs(Collections.emptyList())
                    .build();
        }

        int topK = 8;
        // 动态调整 minScore：根据问题长度和知识库大小
        int knowledgeBaseSize = getKnowledgeBaseSize(tenantId);
        double minScore = calculateMinScore(question, knowledgeBaseSize);
        log.debug("[RAG] 动态 minScore: question={}, knowledgeBaseSize={}, minScore={}", 
                question, knowledgeBaseSize, minScore);

        // 对用户问题检索
        List<RetrievalResultDTO> hits = searchService.search(tenantId, question.trim(), topK, minScore);
        log.debug("[RAG] 检索结果数量: {}, tenantId={}, question={}", 
                hits != null ? hits.size() : 0, tenantId, question);
        
        if (hits == null || hits.isEmpty()) {
            return RagAugmentResult.builder()
                    .augmentedUserMessage(question)
                    .refs(Collections.emptyList())
                    .build();
        }
        // 构建上下文
        String context = buildContext(hits, MAX_CONTEXT_CHARS);
        if (context.isEmpty()) {
            return RagAugmentResult.builder()
                    .augmentedUserMessage(question)
                    .refs(toRefs(hits))
                    .build();
        }

        String augmented = buildRagAugmentedUserMessageForChat(question, context);
        return RagAugmentResult.builder()
                .augmentedUserMessage(augmented)
                .refs(toRefs(hits))
                .build();
    }

    /**
     * 客服场景用：参考资料 + 用户问题。兼顾知识库与常规客服回复。
     */
    private String buildRagAugmentedUserMessageForChat(String question, String context) {
        return """
                以下是本轮知识库检索到的参考资料。若与用户问题相关，请优先结合这些内容作答，并保持客服语气；若无关或资料不足，则按常规客服方式回复。

                【参考资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);
    }

    /**
     * 构造 RAG 用户消息：系统指令 + 检索资料 + 用户问题（独立 RAG 接口用）
     */
    private String buildRagUserMessage(String question, String context) {
        return """
                你是企业内部知识库助手。请严格基于下方【检索到的资料】回答用户的【问题】。
                要求：
                1. 仅根据资料内容作答，不要编造资料中不存在的信息。
                2. 若资料不足以回答问题，请明确说明“根据现有资料无法回答”。
                3. 回答简洁、有条理，可适当分点。

                【检索到的资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);
    }

    // 构建检索内容上下文
    private String buildContext(List<RetrievalResultDTO> hits, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int remain = maxChars;
        int idx = 1;
        for (RetrievalResultDTO h : hits) {
            //格式化之后返回的内容
            String snippet = formatHit(idx++, h);
            if (snippet.length() > remain) {
                break;
            }
            sb.append(snippet).append("\n\n");
            remain -= snippet.length();
        }
        return sb.toString();
    }

    // 格式化检索内容
    private String formatHit(int order, RetrievalResultDTO h) {
        String chunk = h.getChunkText();
        if (chunk != null && chunk.length() > MAX_CHUNK_CHARS) {
            chunk = chunk.substring(0, MAX_CHUNK_CHARS) + "...";
        }
        if (chunk == null) {
            chunk = "";
        }
        return "[片段#" + order + "] 文档《" + safe(h.getTitle()) + "》"
                + (h.getSource() != null ? "，来源：" + h.getSource() : "")
                + "\n" + chunk;
    }

    // 防空处理
    private String safe(String v) {
        return v == null ? "" : v;
    }

    /**
     * 获取知识库大小（按租户统计已索引的 Chunk 数量）
     * 
     * 如果知识库大小变化不频繁，则考虑添加缓存：
     * @Cacheable(value = "knowledge-base-size", key = "#tenantId")
     * 需配置Spring Cache(Redis) 并在文档索引/删除时清除缓存
     * 
     * @param tenantId 租户ID
     * @return 已索引的 Chunk 数量
     */
    private int getKnowledgeBaseSize(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }
        
        // 统计该租户下已索引的 Chunk 数量（qdrantPointId 不为空表示已索引）
        Long count = chunkMapper.selectCount(new LambdaQueryWrapper<Chunk>()
                .eq(Chunk::getTenantId, tenantId)
                .isNotNull(Chunk::getQdrantPointId));
        
        int size = count != null ? count.intValue() : 0;
        log.debug("[RAG] 知识库大小: tenantId={}, size={}", tenantId, size);
        return size;
    }

    /**
     * 动态调整 minScore：根据问题长度和知识库大小
     * 
     * @param question 用户问题
     * @param knowledgeBaseSize 知识库大小（Chunk 数量）
     * @return 调整后的 minScore
     */
    private double calculateMinScore(String question, int knowledgeBaseSize) {
        // 根据问题长度调整
        int len = question.length();
        double baseScore = 0.35;

        if (len < 10) {
            baseScore = 0.3;  // 简短问题，降低阈值
        } else if (len > 30) {
            baseScore = 0.4;  // 长问题，提高阈值
        }

        // 根据知识库大小调整
        if (knowledgeBaseSize < 100) {
            baseScore -= 0.05;  // 知识库小，降低阈值
        } else if (knowledgeBaseSize > 10000) {
            baseScore += 0.05;  // 知识库大，提高阈值
        }

        return Math.max(0.2, Math.min(0.6, baseScore));  // 限制在合理范围
    }

    private List<RagRefDTO> toRefs(List<RetrievalResultDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        return hits.stream()
                .map(h -> RagRefDTO.builder()
                        .documentId(h.getDocumentId())
                        .chunkId(h.getChunkId())
                        .chunkIndex(h.getChunkIndex())
                        .title(h.getTitle())
                        .source(h.getSource())
                        .fileType(h.getFileType())
                        .score(h.getScore())
                        .build())
                .collect(Collectors.toList());
    }
}
