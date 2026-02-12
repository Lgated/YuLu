import http from './axios';
import type { ApiResponse, NotifyMessage } from './types';

/**
 * B端通知API（管理员/客服使用）
 */
export const notifyApi = {
  list(params: { page?: number; size?: number; onlyUnread?: boolean }) {
    return http.get<ApiResponse<{ records: NotifyMessage[]; total: number }>>('/notify/list', {
      params
    });
  },

  // 标记通知为已读
  markRead(notifyIds: number[]) {
    return http.post<ApiResponse<void>>('/notify/read', {
      notifyIds
    });
  }
};


