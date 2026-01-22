package com.ityfz.yulu.customer.controller;

import com.ityfz.yulu.chat.dto.ChatAskRequest;
import com.ityfz.yulu.chat.dto.ChatAskResponse;
import com.ityfz.yulu.chat.dto.CreateSessionRequest;
import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.chat.mapper.ChatSessionMapper;
import com.ityfz.yulu.chat.service.ChatService;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.common.tenant.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/chat")
@RequireRole({"USER","ADMIN"})
@RequiredArgsConstructor
public class CustomerChatController {

    private final ChatService chatService;
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 发送消息给AI（客服对话 + RAG：每轮检索知识库并注入上下文）
     * POST /api/customer/chat/ask
     * 返回 AI 消息 + 本轮 RAG 引用（data.aiMessage、data.refs）
     */
    @PostMapping("/ask")
    public ApiResponse<ChatAskResponse> ask(@RequestBody ChatAskRequest req) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        if (userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "缺少用户信息，请先登录");
        }

        ChatAskResponse res = chatService.chatWithAi(req.getSessionId(), userId, tenantId, req.getQuestion());
        return ApiResponse.success("OK", res);
    }

    /**
     * 查看当前用户的历史消息
     * GET /api/customer/chat/messages/{sessionId}
     *
     * 注意：C端用户只能查看自己的会话
     */
    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        // 校验会话归属：只能查看自己的会话
        chatService.checkSessionOwnerOrAgent(tenantId, userId, sessionId);
        List<ChatMessage> messages = chatService.listMessages(sessionId);
        return ApiResponse.success("OK", messages);
    }

    /**
     * 当前用户的会话列表
     * GET /api/customer/chat/sessions
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> listMySessions() {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        List<ChatSession> sessions = chatService.listUserSessionsByUsers(tenantId, userId);
        return ApiResponse.success("OK", sessions);
    }

    /**
     * 转人工服务
     * POST /api/customer/chat/transfer
     *
     * 功能：当AI无法解决或客户主动要求时，转接人工客服
     */
    @PostMapping("/transfer")
    public ApiResponse<Void> transferToAgent(@RequestParam Long sessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        // TODO: 实现转人工逻辑
        // 1. 创建工单（状态：待分配，优先级：MEDIUM）
        // 2. 通知在线客服
        // 3. 返回成功

        return ApiResponse.success("已转接人工客服，请稍候...", null);
    }

    /**
     * 新增会话
     */
    @PostMapping("/add_session")
    public ApiResponse<ChatSession> createSession(@RequestBody CreateSessionRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        if (userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "缺少用户信息，请先登录");
        }

        Long sessionId = chatService.createSession(userId, tenantId, request.getTitle());
        ChatSession session = chatSessionMapper.selectById(sessionId);

        return ApiResponse.success("会话创建成功", session);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        chatService.deleteSession(tenantId, userId, sessionId);
        return ApiResponse.success("会话删除成功", null);
    }
}
