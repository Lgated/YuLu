import http from './axios';
import type { ApiResponse, LoginRequest, LoginResponse } from './types';

export const authApi = {
  // B端登录（管理员/客服）- 需要tenantCode
  adminLogin(data: LoginRequest) {
    return http.post<ApiResponse<LoginResponse>>('/admin/auth/login', data);
  },
  // C端登录（客户）- 需要租户标识、用户名和密码
  customerLogin(data: { tenantIdentifier: string; username: string; password: string }) {
    return http.post<ApiResponse<LoginResponse>>('/customer/auth/login', data);
  },
  // B端注册（租户注册）- 创建租户+管理员账户
  adminRegisterTenant(data: {
    tenantCode: string;
    tenantName: string;
    adminUsername: string;
    adminPassword: string;
    tenantIdentifier?: string;
  }) {
    return http.post<ApiResponse<any>>('/admin/auth/registerTenant', data);
  },
  // C端注册（客户注册）- 在已有租户下注册客户账户
  customerRegister(data: {
    tenantIdentifier: string;
    username: string;
    password: string;
    nickName?: string;
    email?: string;
    phone?: string;
  }) {
    return http.post<ApiResponse<LoginResponse>>('/customer/auth/register', data);
  },
  // 兼容旧接口（暂时保留，后续移除）
  login(data: LoginRequest) {
    return http.post<ApiResponse<LoginResponse>>('/admin/auth/login', data);
  }
};



