package com.ityfz.yulu.common.exception;

import lombok.Getter;

/**
 * 业务异常，用于返回可预期的错误信息。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final Object data;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BizException(String code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}