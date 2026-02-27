import http from './axios';
import type { ApiResponse } from './types';

export interface DashboardKpi {
  todaySessionCount: number;
  todayHandoffCount: number;
  pendingTicketCount: number;
  onlineAgentCount: number;
  ratingTotalCount?: number;
  ratingAvgScore?: number;
  ratingPositiveRate?: number;
  negativeEmotionCount?: number;
  negativeEmotionRate?: number;
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
  intentDistribution?: IntentDistributionItem[];
  lowScoreAlerts?: LowScoreItem[];
}

export interface IntentDistributionItem {
  intent: string;
  count: number;
}

export interface RatingTrendPoint {
  date: string;
  ratedCount: number;
  avgScore: number;
  positiveRate: number;
}

export interface LowScoreItem {
  id: number;
  handoffRequestId: number;
  sessionId?: number;
  userId: number;
  agentId?: number;
  score?: number;
  comment?: string;
  status: string;
  submitTime?: string;
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
  onlineAgentCount: 0,
  ratingTotalCount: 0,
  ratingAvgScore: 0,
  ratingPositiveRate: 0,
  negativeEmotionCount: 0,
  negativeEmotionRate: 0
};

function normalizeOverview(data: any): DashboardOverview {
  if (data?.kpi && Array.isArray(data?.trend)) {
    return {
      kpi: { ...defaultKpi, ...data.kpi },
      trend: data.trend,
      intentDistribution: normalizeIntentDistribution(data.intentDistribution),
      lowScoreAlerts: normalizeLowScore(data.lowScoreAlerts)
    };
  }

  // 兼容旧版 /stats 结构，保证页面不崩
  const legacy: LegacyDashboardStats = data || {};
  return {
    kpi: {
      todaySessionCount: Number(legacy.todayChatCount || 0),
      todayHandoffCount: 0,
      pendingTicketCount: Number(legacy.ticketCount || 0),
      onlineAgentCount: 0,
      ratingTotalCount: 0,
      ratingAvgScore: Number(legacy.satisfactionRate || 0) / 20,
      ratingPositiveRate: Number(legacy.satisfactionRate || 0),
      negativeEmotionCount: 0,
      negativeEmotionRate: 0
    },
    trend: [],
    intentDistribution: [],
    lowScoreAlerts: []
  };
}

function normalizeIntentDistribution(data: any): IntentDistributionItem[] {
  if (!Array.isArray(data)) return [];
  return data.map((item) => ({
    intent: item.intent || 'GENERAL',
    count: Number(item.count || 0)
  }));
}

function normalizeRatingTrend(data: any): RatingTrendPoint[] {
  if (!Array.isArray(data)) return [];
  return data.map((item) => ({
    date: item.date,
    ratedCount: Number(item.ratedCount || 0),
    avgScore: Number(item.avgScore || 0),
    positiveRate: Number(item.positiveRate || 0)
  }));
}

function normalizeLowScore(data: any): LowScoreItem[] {
  if (!Array.isArray(data)) return [];
  return data.map((item) => ({
    id: Number(item.id),
    handoffRequestId: Number(item.handoffRequestId),
    sessionId: item.sessionId == null ? undefined : Number(item.sessionId),
    userId: Number(item.userId),
    agentId: item.agentId == null ? undefined : Number(item.agentId),
    score: item.score == null ? undefined : Number(item.score),
    comment: item.comment,
    status: item.status || '',
    submitTime: item.submitTime
  }));
}

export const dashboardApi = {
  async overview(days = 7) {
    const params = { days };

    try {
      const res = await http.get<ApiResponse<any>>('/admin/dashboard/overview', { params });
      return normalizeOverview(res.data?.data ?? res.data);
    } catch {
      const res = await http.get<ApiResponse<any>>('/admin/dashboard/stats', { params });
      return normalizeOverview(res.data?.data ?? res.data);
    }
  },

  async ratingTrend(days = 7): Promise<RatingTrendPoint[]> {
    const res = await http.get<ApiResponse<any>>('/admin/handoff/rating/trend', { params: { days } });
    return normalizeRatingTrend(res.data?.data ?? res.data);
  },

  async lowScoreTop(days = 7, limit = 5, maxScore = 2): Promise<LowScoreItem[]> {
    const res = await http.get<ApiResponse<any>>('/admin/handoff/rating/low-score/top', {
      params: { days, limit, maxScore }
    });
    return normalizeLowScore(res.data?.data ?? res.data);
  }
};
