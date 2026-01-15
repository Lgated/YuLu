package com.ityfz.yulu.admin.controller;


import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.chat.service.ChatService;
import com.ityfz.yulu.common.annotation.RequireRole;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * B端会话管理Controller
 * 权限要求：ADMIN 或 AGENT
 */
@RestController
@RequestMapping("/api/admin/session")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
public class AdminSessionController {

    private final ChatService chatService;

    /**
     * 获取当前租户下所有会话（管理员/客服可查看）
     * GET /api/admin/session/list
     */
    @GetMapping("/list")
    public ApiResponse<List<ChatSession>> listAllSessions() {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        List<ChatSession> sessions = chatService.listAllSessionsByTenant(tenantId);
        return ApiResponse.success("OK", sessions);
    }

    /**
     * 查看某个用户的会话列表
     * GET /api/admin/session/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<ChatSession>> listUserSessions(@PathVariable Long userId) {
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        List<ChatSession> sessions = chatService.listUserSessionsByUsers(tenantId, userId);
        return ApiResponse.success("OK", sessions);
    }

    /**
     * 查看会话消息列表
     * GET /api/admin/session/{sessionId}/messages
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();

        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }

        // 管理员/客服可以查看所有会话
        chatService.checkSessionOwnerOrAgent(tenantId, userId, sessionId);
        List<ChatMessage> messages = chatService.listMessages(sessionId);
        return ApiResponse.success("OK", messages);
    }

}

