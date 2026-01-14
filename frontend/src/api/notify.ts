import http from './axios';
import type { ApiResponse, NotifyMessage } from './types';

export const notifyApi = {
  list(params: { page?: number; size?: number; onlyUnread?: boolean }) {
    return http.get<ApiResponse<{ records: NotifyMessage[]; total: number }>>('/notify/list', {
      params
    });
  }
};


