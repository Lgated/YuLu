import http from './axios';
import type { ApiResponse, HandoffTransferRequest, HandoffTransferResponse, HandoffStatusResponse } from './types';

/**
 * 转人工（Handoff）相关 API
 */
export const handoffApi = {
  /**
   * 客户申请转人工
   */
  requestTransfer(payload: HandoffTransferRequest) {
    return http.post<ApiResponse<HandoffTransferResponse>>('/customer/handoff/transfer', payload);
  },

  /**
   * 客户查询转人工状态
   */
  getStatus(handoffRequestId: number) {
    return http.get<ApiResponse<HandoffStatusResponse>>(`/customer/handoff/status/${handoffRequestId}`);
  },

  /**
   * 客户取消转人工
   */
  cancel(handoffRequestId: number) {
    return http.post<ApiResponse<void>>(`/customer/handoff/cancel/${handoffRequestId}`);
  },

  /**
   * 客服获取待处理的转人工请求列表
   */
  getPendingRequests() {
    return http.get<ApiResponse<any[]>>('/agent/handoff/pending');
  },

  /**
   * 客服接受转人工请求
   */
  accept(handoffRequestId: number) {
    return http.post<ApiResponse<any>>('/agent/handoff/accept', { handoffRequestId });
  },

  /**
   * 客服拒绝转人工请求
   */
  reject(handoffRequestId: number, reason?: string) {
    return http.post<ApiResponse<void>>('/agent/handoff/reject', { handoffRequestId, reason });
  },

  /**
   * 客服完成转人工对话
   */
  complete(handoffRequestId: number) {
    return http.post<ApiResponse<void>>(`/agent/handoff/complete/${handoffRequestId}`);
  },

  /**
   * 用户结束人工对话（仅在已接入/进行中时允许）
   */
  endByUser(handoffRequestId: number) {
    return http.post<ApiResponse<void>>('/customer/handoff/end-by-user', { handoffRequestId });
  },

  /**
   * 根据会话ID获取转人工请求信息
   */
  getBySessionId(sessionId: number) {
    return http.get<ApiResponse<any>>(`/agent/handoff/by-session/${sessionId}`);
  }
};
