import http from './axios';
import type { ApiResponse, AgentMonitor, HandoffRatingRecord, HandoffRatingStats, HandoffRecord } from './types';

export const adminHandoffApi = {
  listRecords(params: {
    userId?: number;
    agentId?: number;
    status?: string;
    startTime?: string;
    endTime?: string;
    pageNo?: number;
    pageSize?: number;
  }) {
    return http.get<
      ApiResponse<{
        records: HandoffRecord[];
        total: number;
        current?: number;
        size?: number;
        pages?: number;
      }>
    >('/admin/handoff/records', { params });
  },

  getAgentStatus() {
    return http.get<ApiResponse<AgentMonitor[]>>('/admin/handoff/agent/status');
  },

  forceAgentStatus(agentId: number, status: string) {
    return http.post<ApiResponse<void>>(`/admin/handoff/agent/${agentId}/status`, {
      status
    });
  },

  broadcastNotification(title: string, content: string) {
    return http.post<ApiResponse<void>>('/admin/handoff/notify', {
      title,
      content
    });
  },

  listRatings(params: {
    agentId?: number;
    score?: number;
    status?: string;
    startTime?: string;
    endTime?: string;
    pageNo?: number;
    pageSize?: number;
  }) {
    return http.get<
      ApiResponse<{
        records: HandoffRatingRecord[];
        total: number;
        current?: number;
        size?: number;
        pages?: number;
      }>
    >('/admin/handoff/rating/list', { params });
  },

  getRatingStats() {
    return http.get<ApiResponse<HandoffRatingStats>>('/admin/handoff/rating/stats');
  },

  processRating(ratingId: number, note?: string) {
    return http.post<ApiResponse<void>>(`/admin/handoff/rating/${ratingId}/process`, { note });
  }
};
