import http from './axios';
import type { ApiResponse, AgentMonitor, HandoffRecord } from './types';

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
  }
};
