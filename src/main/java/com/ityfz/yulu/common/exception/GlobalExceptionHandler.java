package com.ityfz.yulu.common.exception;

import com.ityfz.yulu.common.error.ErrorCodes;
import com.ityfz.yulu.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;


/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //业务异常
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage(), e.getData());
    }

    // 参数校验相关
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleValidation(Exception e) {
        String msg = resolveValidationMsg(e);
        log.warn("参数校验失败: {}", msg, e);
        return ApiResponse.fail(ErrorCodes.VALIDATION_ERROR, msg);
    }

    // 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("方法不允许: {}", e.getMessage());
        return ApiResponse.fail(ErrorCodes.METHOD_NOT_ALLOWED, "请求方法不被允许");
    }

    // 兜底异常
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        log.error("系统异常: ", e);
        return ApiResponse.fail(ErrorCodes.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }

    private String resolveValidationMsg(Exception e) {
        if (e instanceof MethodArgumentNotValidException ex) {
            return ex.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getField() + " " + err.getDefaultMessage())
                    .orElse("参数校验失败");
        }
        if (e instanceof BindException ex) {
            return ex.getAllErrors().stream()
                    .findFirst()
                    .map(err -> err.getDefaultMessage())
                    .orElse("参数绑定失败");
        }
        if (e instanceof MissingServletRequestParameterException ex) {
            return "缺少参数: " + ex.getParameterName();
        }
        if (e instanceof ConstraintViolationException ex) {
            return ex.getConstraintViolations().stream()
                    .findFirst()
                    .map(v -> v.getMessage())
                    .orElse("参数校验失败");
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体格式错误";
        }
        return "参数校验失败";
    }
}


