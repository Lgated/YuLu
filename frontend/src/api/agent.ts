import http from './axios';
import type { ApiResponse } from './types';

/**
 * 客服相关API
 */
export const agentApi = {
  /**
   * 更新在线状态
   * @param status ONLINE | OFFLINE | AWAY
   */
  updateOnlineStatus(status: 'ONLINE' | 'OFFLINE' | 'AWAY') {
    return http.put<ApiResponse<void>>('/admin/user/online-status', null, {
      params: { status }
    });
  },

  /**
   * 心跳接口（保持在线状态）
   */
  heartbeat() {
    return http.post<ApiResponse<void>>('/admin/auth/heartbeat');
  }
};


