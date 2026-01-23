import http from './axios';
import type { ApiResponse, Ticket, TicketComment } from './types';

/**
 * B端工单管理API（管理员/客服使用）
 */
export const ticketApi = {
  // 分页查询工单列表
  list(params: { status?: string; assigneeId?: number; page?: number; size?: number }) {
    // 后端返回 MyBatis-Plus IPage：{ records, total, current, size, pages ... }
    return http.get<
      ApiResponse<{
        records: Ticket[];
        total: number;
        current?: number;
        size?: number;
        pages?: number;
      }>
    >('/admin/ticket/list', {
      params
    });
  },


  // 工单状态流转
  transition(ticketId: number, targetStatus: string, comment?: string) {
    return http.post<ApiResponse<void>>('/admin/ticket/transition', {
      ticketId,
      targetStatus,
      comment
    });
  },

  // 添加工单备注
  addComment(ticketId: number, content: string) {
    return http.post<ApiResponse<void>>('/admin/ticket/comment', {
      ticketId,
      content
    });
  },

  // 获取工单备注列表
  comments(ticketId: number) {
    return http.get<ApiResponse<TicketComment[]>>('/admin/ticket/comment/list', {
      params: { ticketId }
    });
  },

  // 获取工单统计信息
  stats() {
    return http.get<ApiResponse<any>>('/admin/ticket/stats');
  },


  // 获取客服列表（管理员派单用）
  getAgents() {
    return http.get<ApiResponse<any[]>>('/admin/ticket/agents');
  },

  // 派单
  assign(request: { ticketId: number; assigneeUserId: number }) {
    return http.post<ApiResponse<void>>('/admin/ticket/assign', request);
  }
};