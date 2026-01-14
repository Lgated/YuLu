import http from './axios';
import type { ApiResponse, ChatMessage, ChatSession } from './types';

export const chatApi = {
  ask(payload: { sessionId?: number | null; question: string }) {
    return http.post<ApiResponse<ChatMessage>>('/chat/ask', payload);
  },

  messages(sessionId: number) {
    return http.get<ApiResponse<ChatMessage[]>>(`/chat/messages/${sessionId}`);
  },

  allSessions() {
    return http.get<ApiResponse<ChatSession[]>>('/chat/sessions/all');
  }
};


