package com.iot.api.exception;

import com.iot.common.exception.BusinessException;
import com.iot.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 使用 @RestControllerAdvice 统一捕获和处理控制器层抛出的异常，
 * 将异常信息转换为统一的 Result 格式返回给前端，避免直接暴露异常堆栈。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * <p>
     * 捕获 Service 层抛出的 BusinessException，提取错误码和错误消息，
     * 以标准 Result 格式返回给前端。BusinessException 包含业务语义的错误码，
     * 前端可以根据错误码进行相应的业务提示。
     * </p>
     *
     * @param e 业务异常
     * @return 统一响应结果（包含业务错误码和消息）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 - code: {}, message: {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     * <p>
     * 捕获 @Valid 或 @Validated 参数校验失败抛出的 MethodArgumentNotValidException，
     * 提取第一个校验失败的字段名和错误消息返回，便于前端定位输入问题。
     * </p>
     *
     * @param e 参数校验异常
     * @return 统一响应结果（HTTP 400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 提取第一个校验失败的字段错误信息
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "请求参数校验失败";
        log.warn("参数校验异常 - {}", message);
        return Result.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 处理未知异常（兜底）
     * <p>
     * 兜底处理所有未被特定异常处理器捕获的异常。
     * 记录完整的异常堆栈信息以方便排查问题，但只返回通用的错误提示给前端，
     * 避免暴露内部实现细节。
     * </p>
     *
     * @param e 未知异常
     * @return 统一响应结果（HTTP 500）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部异常", e);
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误");
    }
}
