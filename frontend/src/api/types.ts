export interface ApiResponse<T> {
  success?: boolean;  // 后端返回的success字段
  code: string;       // 后端返回的code字段（如"200"表示成功）
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
  role?: string;
  username?: string;
  tenantId?: number;
  userId?: number;
}

export interface ChatMessage {
  id: number;
  tenantId: number;
  sessionId: number;
  senderType: 'USER' | 'AI' | 'AGENT';
  content: string;
  emotion: string;
  createTime: string;
  refs?: RagRef[]; // 可选：RAG 引用（仅 AI 消息有）
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


/**
 * 聊天请求响应（包含引用）
 */
export interface ChatAskResponse {
  aiMessage: ChatMessage;
  refs: RagRef[];
}

/**
 * RAG 引用
 */
export interface RagRef {
  documentId: number;
  chunkId: number;
  chunkIndex: number;
  title: string;  // 文档标题（用于显示）
  source?: string;
  fileType?: string;
  score: number;
}

/**
 * 会话响应（增强版，含统计信息）
 */
export interface SessionResponse {
  id: number;
  title: string;
  createTime: string;
  updateTime: string;
  messageCount: number;
  lastMessageTime?: string;
  lastMessagePreview?: string;
}

/**
 * 文档列表项
 */
export interface DocumentListItem {
  id: number;
  title: string;
  source?: string;
  fileType?: string;
  fileSize?: number;
  status: number; // 0-未索引，1-已索引
  createTime: string;
}

/**
 * 文档详情
 */
export interface DocumentDetail {
  id: number;
  title: string;
  source?: string;
  fileType?: string;
  fileSize?: number;
  status: number;
  indexedAt?: string;
  createTime: string;
  updateTime: string;
  contentPreview?: string;
}

export interface UserResponse {
  id: number;
  tenantId: number;
  username: string;
  role: string;
  status: number;
  nickName?: string;
  email?: string;
  phone?: string;
  createTime: string;
  updateTime: string;
  statusText: string;
  roleText: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  role: string;
  nickName?: string;
  email?: string;
  phone?: string;
  status?: number;
}

export interface UpdateUserRequest {
  nickName?: string;
  email?: string;
  phone?: string;
  role?: string;
  status?: number;
}


/**
 * 客户申请转人工的请求体
 */
export interface HandoffTransferRequest {
  sessionId: number;
  reason?: string;
}

/**
 * 申请转人工的响应体
 */
export interface HandoffTransferResponse {
  handoffRequestId: number;
  ticketId: number;
  queuePosition?: number;
  estimatedWaitTime?: number;
  fallback: boolean;
  fallbackMessage?: string;
}

/**
 * 查询转人工状态的响应体
 */
export interface HandoffStatusResponse {
  handoffRequestId: number;
  status: 'PENDING' | 'ASSIGNED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'CANCELLED' | 'FALLBACK_TICKET';
  queuePosition?: number;
  estimatedWaitTime?: number;
  assignedAgentId?: number;
  assignedAgentName?: string;
}