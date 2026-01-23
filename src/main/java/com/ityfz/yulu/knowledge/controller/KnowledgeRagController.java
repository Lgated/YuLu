package com.ityfz.yulu.knowledge.controller;

import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.knowledge.dto.RagChatRequest;
import com.ityfz.yulu.knowledge.dto.RagChatResponse;
import com.ityfz.yulu.knowledge.service.KnowledgeChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 知识库 RAG 对话接口
 *
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库-RAG（Knowledge/RAG）", description = "RAG 对话（目前标注为暂不用，但仍保留接口文档）")
public class KnowledgeRagController {

    private final KnowledgeChatService knowledgeChatService;

    /**
     * RAG 对话
     * POST /api/admin/knowledge/chat
     * 暂时不用
     */
    @PostMapping("/chat")
    @Operation(summary = "RAG 对话（暂不用）", description = "独立 RAG 对话接口（你当前主要在 ChatServiceImpl 内集成 RAG，此接口可作为调试/对比）")
    public ApiResponse<RagChatResponse> chat(@Valid @RequestBody RagChatRequest request) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "请先登录");
        }
        RagChatResponse response = knowledgeChatService.ragChat(tenantId, request);
        return ApiResponse.success("OK", response);
    }
}

