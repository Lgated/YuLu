package com.ityfz.yulu.common.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体。
 */
@Data
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setSuccess(true);
        resp.setCode("200");
        resp.setMessage("OK");
        resp.setData(data);
        return resp;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setSuccess(true);
        resp.setCode("200");
        resp.setMessage(message);
        resp.setData(data);
        return resp;
    }

    public static <T> ApiResponse<T> success(){
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setSuccess(true);
        resp.setCode("200");
        resp.setMessage("操作成功");
        resp.setData(null);
        return resp;
    }
    public static <T> ApiResponse<T> success(String message){
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setSuccess(true);
        resp.setCode("200");
        resp.setMessage(message);
        resp.setData(null);
        return resp;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return fail(code, message, null);
    }
    public static <T> ApiResponse<T> fail(String code, String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }
}