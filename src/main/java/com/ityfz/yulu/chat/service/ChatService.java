package com.ityfz.yulu.chat.service;

import com.ityfz.yulu.chat.dto.ChatAskResponse;
import com.ityfz.yulu.chat.dto.EditSessionRequest;
import com.ityfz.yulu.chat.entity.ChatMessage;
import com.ityfz.yulu.chat.entity.ChatSession;

import java.util.List;
import java.util.Map;

public interface ChatService {

    // 创建会话，如果不存在的话。
    Long createSessionIfNotExists(Long userId, Long tenantId, String title);

    // 用户发送消息。
    ChatMessage userSendMessage(Long sessionId, String content);

    // 获取消息列表。
    List<ChatMessage> listMessages(Long sessionId);

    // 追加AI回复。
    //方便后续 AI 回调。
    void appendAiReply(Long sessionId, String aiContent, String emotion);

    // 从Redis中获取对话上下文。
    //返回最近上下文，供调用模型或调试。
    List<Map<String, String>> listContextFromRedis(Long sessionId);

    /**
     * 用户发起一次“问 + AI 答”的完整流程（含 RAG：每轮检索知识库并注入上下文）。
     * 返回 AI 消息 + 本轮 RAG 引用。
     */
    ChatAskResponse chatWithAi(Long sessionId, Long userId, Long tenantId, String question);

    // 获取租户下所有会话
    List<ChatSession> listAllSessionsByTenant(Long tenantId);

    // 获取租户下某个用户参与的会话
    List<ChatSession> listUserSessionsByUsers(Long tenantId, Long userId);

    // 检查会话归属，只有用户本人或管理员可以查看
    void checkSessionOwnerOrAgent(Long tenantId, Long userId, Long sessionId);

    /**
     * 创建新会话
     */
    Long createSession(Long userId, Long tenantId, String title);

    /**
     * 删除会话（软删除）
     */
    void deleteSession(Long tenantId, Long userId, Long sessionId);

    /**
     *  编辑会话名称
     */
    Long editSession(Long userId, Long tenantId, EditSessionRequest request);
}
