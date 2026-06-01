package com.iot.common.exception;

/**
 * 订单异常类
 * <p>
 * 当充电订单相关的业务操作出现冲突或异常时抛出此异常。
 * 例如：订单状态不允许当前操作、订单不存在、订单重复支付等。
 * 默认错误码为 409（冲突）。
 * </p>
 *
 * @author IoT Team
 */
public class OrderException extends BusinessException {

    /**
     * 构造方法
     *
     * @param message 错误消息
     */
    public OrderException(String message) {
        super(409, message);
    }

    /**
     * 构造方法
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public OrderException(int code, String message) {
        super(code, message);
    }
}
