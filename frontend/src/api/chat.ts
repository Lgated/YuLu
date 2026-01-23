import http from './axios';
import type { ApiResponse, ChatMessage, ChatSession, ChatAskResponse } from './types';

/**
 * C端聊天API（客户使用）
 */
export const chatApi = {
  // 发送消息给AI（返回包含引用的响应）
  ask(payload: { sessionId?: number | null; question: string }) {
    return http.post<ApiResponse<ChatAskResponse>>('/customer/chat/ask', payload);
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
  },

  // 创建新会话
  createSession(title?: string) {
    return http.post<ApiResponse<ChatSession>>('/customer/chat/add_session', { title });
  },

  // 删除会话
  deleteSession(sessionId: number) {
    return http.delete<ApiResponse<void>>(`/customer/chat/sessions/${sessionId}`);
  },

  // 编辑会话名称
  editSession(sessionId: number, newTitle: string) {
    return http.put<ApiResponse<ChatSession>>('/customer/chat/edit', {
      id: sessionId,
      newTitle
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

/**
 * 知识库管理API（管理员使用）
 */
export const knowledgeApi = {
  // 上传文档
  uploadDocument(file: File, title?: string, source?: string) {
    const formData = new FormData();
    formData.append('file', file);
    if (title) formData.append('title', title);
    if (source) formData.append('source', source);
    
    return http.post<ApiResponse<number>>('/admin/document/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },

  // 获取文档列表
  listDocuments(pageNum?: number, pageSize?: number) {
    return http.get<ApiResponse<any[]>>('/admin/document/list', {
      params: { pageNum, pageSize }
    });
  },

  // 获取文档详情
  getDocumentDetail(documentId: number) {
    return http.get<ApiResponse<any>>(`/admin/document/${documentId}`);
  },

  // 删除文档
  deleteDocument(documentId: number) {
    return http.delete<ApiResponse<void>>(`/admin/document/${documentId}`);
  },

  // 索引文档
  indexDocument(documentId: number) {
    return http.post<ApiResponse<void>>(`/admin/knowledge/document/${documentId}/index`);
  }
};



