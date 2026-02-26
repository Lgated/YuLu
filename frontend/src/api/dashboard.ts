import http from './axios';
import type { ApiResponse } from './types';

export interface DashboardKpi {
  todaySessionCount: number;
  todayHandoffCount: number;
  pendingTicketCount: number;
  onlineAgentCount: number;
  refreshTime?: string;
}

export interface DashboardTrendPoint {
  date: string;
  sessionCount: number;
  handoffCount: number;
}

export interface DashboardOverview {
  kpi: DashboardKpi;
  trend: DashboardTrendPoint[];
}

export interface LegacyDashboardStats {
  todayChatCount?: number;
  aiResolveRate?: number;
  transferRate?: number;
  ticketCount?: number;
  avgResponseTime?: number;
  satisfactionRate?: number;
}

const defaultKpi: DashboardKpi = {
  todaySessionCount: 0,
  todayHandoffCount: 0,
  pendingTicketCount: 0,
  onlineAgentCount: 0
};

function normalizeOverview(data: any): DashboardOverview {
  if (data?.kpi && Array.isArray(data?.trend)) {
    return {
      kpi: { ...defaultKpi, ...data.kpi },
      trend: data.trend
    };
  }

  // 兼容旧版 /stats 结构，保证页面不崩
  const legacy: LegacyDashboardStats = data || {};
  return {
    kpi: {
      todaySessionCount: Number(legacy.todayChatCount || 0),
      todayHandoffCount: 0,
      pendingTicketCount: Number(legacy.ticketCount || 0),
      onlineAgentCount: 0
    },
    trend: []
  };
}

export const dashboardApi = {
  async overview(days = 7) {
    const params = { days };

    try {
      const res = await http.get<ApiResponse<any>>('/admin/dashboard/overview', { params });
      return normalizeOverview(res.data);
    } catch {
      const res = await http.get<ApiResponse<any>>('/admin/dashboard/stats', { params });
      return normalizeOverview(res.data);
    }
  }
};
