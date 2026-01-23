import http from './axios';
import type { ApiResponse } from './types';

export interface UserListRequest {
  role?: string;
  status?: number;
  keyword?: string;
  page?: number;
  size?: number;
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

export interface ResetPasswordRequest {
  newPassword: string;
}

export const userManagementApi = {
  // 查询用户列表
  list(params: UserListRequest) {
    return http.get<ApiResponse<any>>('/admin/user-management/list', { params });
  },

  // 获取用户详情
  getById(userId: number) {
    return http.get<ApiResponse<UserResponse>>(`/admin/user-management/${userId}`);
  },

  // 创建用户
  create(data: CreateUserRequest) {
    return http.post<ApiResponse<UserResponse>>('/admin/user-management/create', data);
  },

  // 更新用户
  update(userId: number, data: UpdateUserRequest) {
    return http.put<ApiResponse<UserResponse>>(`/admin/user-management/${userId}`, data);
  },

  // 重置密码
  resetPassword(userId: number, data: ResetPasswordRequest) {
    return http.post<ApiResponse<void>>(`/admin/user-management/${userId}/reset-password`, data);
  },

  // 删除用户
  delete(userId: number) {
    return http.delete<ApiResponse<void>>(`/admin/user-management/${userId}`);
  },

  // 更新用户状态
  updateStatus(userId: number, status: number) {
    return http.put<ApiResponse<void>>(`/admin/user-management/${userId}/status`, null, {
      params: { status }
    });
  }
};