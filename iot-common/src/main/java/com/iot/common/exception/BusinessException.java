package com.iot.common.exception;

/**
 * 业务异常类
 * <p>
 * 所有业务逻辑异常的基类，继承自 RuntimeException（非受检异常）。
 * 包含错误码和错误消息，便于全局异常处理器统一处理和响应。
 * 默认错误码为 500（服务器内部错误）。
 * </p>
 *
 * @author IoT Team
 */
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法（使用默认错误码 500）
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    // ==================== Getters ====================

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "code=" + code +
                ", message=" + getMessage() +
                '}';
    }
}
