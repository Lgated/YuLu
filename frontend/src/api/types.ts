export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface LoginRequest {
  tenantCode: string;
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export interface ChatMessage {
  id: number;
  tenantId: number;
  sessionId: number;
  senderType: 'USER' | 'AI' | 'AGENT';
  content: string;
  emotion: string;
  createTime: string;
}

export interface ChatSession {
  id: number;
  tenantId: number;
  userId: number;
  sessionTitle: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface Ticket {
  id: number;
  tenantId: number;
  userId: number;
  sessionId: number | null;
  status: string;
  priority: string;
  assignee: number | null;
  title: string;
  description: string;
  createTime: string;
  updateTime: string;
}

export interface TicketComment {
  id: number;
  ticketId: number;
  tenantId: number;
  userId: number;
  content: string;
  createTime: string;
}

export interface NotifyMessage {
  id: number;
  tenantId: number;
  userId: number;
  type: string;
  title: string;
  content: string;
  readFlag: number;
  createTime: string;
}


