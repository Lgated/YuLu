import http from './axios';
import type { ApiResponse, Ticket, TicketComment } from './types';

export const ticketApi = {
  list(params: { status?: string; page?: number; size?: number }) {
    return http.get<ApiResponse<{ records: Ticket[]; total: number }>>('/ticket/list', {
      params
    });
  },

  comments(ticketId: number) {
    return http.get<ApiResponse<TicketComment[]>>('/ticket/comment/list', {
      params: { ticketId }
    });
  }
};


