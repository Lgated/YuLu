import http from './axios';
import type { ApiResponse, FaqCategory, FaqItem } from './types';

export const customerFaqApi = {
  categories() {
    return http.get<ApiResponse<FaqCategory[]>>('/customer/faq/categories');
  },
  list(params: { categoryId?: number; keyword?: string; page?: number; size?: number }) {
    return http.get<
      ApiResponse<{
        records: FaqItem[];
        total: number;
        current?: number;
        size?: number;
      }>
    >('/customer/faq/list', { params });
  },
  hot(limit = 10) {
    return http.get<ApiResponse<FaqItem[]>>('/customer/faq/hot', { params: { limit } });
  },
  view(faqId: number) {
    return http.post<ApiResponse<void>>(`/customer/faq/view/${faqId}`);
  },
  feedback(payload: { faqId: number; feedbackType: 1 | 0 }) {
    return http.post<ApiResponse<void>>('/customer/faq/feedback', payload);
  }
};

export const adminFaqApi = {
  categories() {
    return http.get<ApiResponse<FaqCategory[]>>('/admin/faq/categories');
  },
  createCategory(payload: { name: string; sort?: number; status?: number }) {
    return http.post<ApiResponse<number>>('/admin/faq/category', payload);
  },
  updateCategory(id: number, payload: { name: string; sort?: number; status?: number }) {
    return http.put<ApiResponse<void>>(`/admin/faq/category/${id}`, payload);
  },
  deleteCategory(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/faq/category/${id}`);
  },
  listItems(params: { categoryId?: number; keyword?: string; page?: number; size?: number }) {
    return http.get<
      ApiResponse<{
        records: FaqItem[];
        total: number;
        current?: number;
        size?: number;
      }>
    >('/admin/faq/item/list', { params });
  },
  createItem(payload: {
    categoryId: number;
    question: string;
    answer: string;
    keywords?: string;
    sort?: number;
    status?: number;
  }) {
    return http.post<ApiResponse<number>>('/admin/faq/item', payload);
  },
  updateItem(
    id: number,
    payload: {
      categoryId: number;
      question: string;
      answer: string;
      keywords?: string;
      sort?: number;
      status?: number;
    }
  ) {
    return http.put<ApiResponse<void>>(`/admin/faq/item/${id}`, payload);
  },
  updateItemStatus(id: number, status: number) {
    return http.put<ApiResponse<void>>(`/admin/faq/item/${id}/status`, null, {
      params: { status }
    });
  },
  deleteItem(id: number) {
    return http.delete<ApiResponse<void>>(`/admin/faq/item/${id}`);
  }
};
