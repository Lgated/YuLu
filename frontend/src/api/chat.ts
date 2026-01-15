import http from './axios';
import type { ApiResponse, ChatMessage, ChatSession } from './types';

/**
 * C端聊天API（客户使用）
 */
export const chatApi = {
  // 发送消息给AI
  ask(payload: { sessionId?: number | null; question: string }) {
    return http.post<ApiResponse<ChatMessage>>('/customer/chat/ask', payload);
  },

  // 当前用户的会话列表（C端）
  sessions() {
    return http.get<ApiResponse<ChatSession[]>>('/customer/chat/sessions');
  },

  // 查看当前用户的历史消息
  messages(sessionId: number) {
    return http.get<ApiResponse<ChatMessage[]>>(`/customer/chat/messages/${sessionId}`);
  },

  // 转人工服务
  transferToAgent(sessionId: number) {
    return http.post<ApiResponse<void>>('/customer/chat/transfer', null, {
      params: { sessionId }
    });
  }
};

/**
 * B端会话管理API（管理员/客服使用）
 */
export const sessionApi = {
  // 获取当前租户下所有会话
  listAllSessions() {
    return http.get<ApiResponse<ChatSession[]>>('/admin/session/list');
  },

  // 查看某个用户的会话列表
  listUserSessions(userId: number) {
    return http.get<ApiResponse<ChatSession[]>>(`/admin/session/user/${userId}`);
  },

  // 查看会话消息列表
  listMessages(sessionId: number) {
    return http.get<ApiResponse<ChatMessage[]>>(`/admin/session/${sessionId}/messages`);
  }
};



