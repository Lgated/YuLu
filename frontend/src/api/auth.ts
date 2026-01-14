import http from './axios';
import type { ApiResponse, LoginRequest, LoginResponse } from './types';

export const authApi = {
  login(data: LoginRequest) {
    return http.post<ApiResponse<LoginResponse>>('/auth/login', data);
  }
};


