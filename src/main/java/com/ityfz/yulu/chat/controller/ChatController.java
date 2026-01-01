package com.ityfz.yulu.chat.controller;

import com.ityfz.yulu.chat.dto.ChatAskRequest;
import com.ityfz.yulu.chat.entity.ChatSession;
import com.ityfz.yulu.common.error.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.model.ApiResponse;
import com.ityfz.yulu.common.security.SecurityUtil;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.common.tenant.UserContextHolder;
import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    public ApiResponse<ChatMessage> ask(@RequestBody ChatAskRequest req){
        // 从 ThreadLocal / Token 中取出 tenantId 和 userId
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = UserContextHolder.getUserId();

        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        if (userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "缺少用户信息，请先登录");
        }

        ChatMessage aiMsg = chatService.chatWithAi(req.getSessionId(), userId, tenantId, req.getQuestion());
        return ApiResponse.success("OK", aiMsg);
    }


    //查看聊天记录 -- 普通 USER 只能看自己的会话
    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        Long tenantId = SecurityUtil.currentTenantId();
        Long userId = SecurityUtil.currentUserId();
        if (tenantId == null || userId == null) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, "请先登录");
        }
        // 这里可以做一次“会话归属校验”：当前用户是否有权访问这个 session
        chatService.checkSessionOwnerOrAgent(tenantId, userId, sessionId);
        List<ChatMessage> messages = chatService.listMessages(sessionId);
        return ApiResponse.success("OK", messages);
    }

    // 获取当前租户下所有会话（只允许 ADMIN）
    @GetMapping("/sessions/all")
    public ApiResponse<List<ChatSession>> listAllSessionsForTenant() {
        SecurityUtil.checkAdmin();  // 先做角色校验

        //获取当前租户id
        Long tenantId = SecurityUtil.currentTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }

        List<ChatSession> sessions = chatService.listAllSessionsByTenant(tenantId);
        return ApiResponse.success("OK", sessions);
    }

    //客服或者管理员查看某个用户的会话列表
    @GetMapping("/sessions/user/{userId}")
    public ApiResponse<List<ChatSession>> listUserSessions(@PathVariable Long userId){
        SecurityUtil.checkAdmin();  // 先做角色校验
        Long tenantId = SecurityUtil.currentTenantId();
        if(tenantId == null){
            throw new BizException(ErrorCodes.TENANT_REQUIRED, "缺少租户信息，请先登录");
        }
        List<ChatSession> sessions = chatService.listUserSessionsByUsers(tenantId,userId);
        return ApiResponse.success("OK", sessions);
    }
}
