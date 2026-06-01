package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.dto.response.OrderVO;

import java.time.LocalDateTime;

/**
 * 订单服务接口
 * <p>
 * 提供订单查询、支付和退款功能。
 * 采用乐观锁（version字段）防止并发支付/退款冲突。
 * </p>
 *
 * @author IoT Team
 */
public interface OrderService {

    /**
     * 分页查询用户订单列表
     * <p>
     * 支持按订单状态、时间范围筛选，订单按创建时间倒序排列。
     * 关联查询充电桩名称和充电站名称。
     * </p>
     *
     * @param userId      用户ID
     * @param page        页码
     * @param size        每页数量
     * @param orderStatus 订单状态筛选（可选）
     * @param startTime   开始时间筛选（可选）
     * @param endTime     结束时间筛选（可选）
     * @return 分页订单列表
     */
    PageResult<OrderVO> listOrders(Long userId, int page, int size,
                                    Integer orderStatus, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取订单详情
     * <p>
     * 校验订单是否属于当前用户。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 订单详情
     */
    OrderVO getOrderDetail(Long orderId, Long userId);

    /**
     * 模拟支付
     * <p>
     * 1. 校验订单属于当前用户
     * 2. 校验 payStatus=UNPAID 且 orderStatus=COMPLETED
     * 3. 使用乐观锁更新 payStatus=PAID
     * 4. 发送 RocketMQ 支付完成事件
     * </p>
     *
     * @param orderNo 订单编号
     * @param userId  用户ID
     */
    void payOrder(String orderNo, Long userId);

    /**
     * 申请退款
     * <p>
     * 1. 校验订单属于当前用户
     * 2. 校验 payStatus=PAID
     * 3. 更新 payStatus=REFUNDED
     * </p>
     *
     * @param orderNo 订单编号
     * @param userId  用户ID
     */
    void refundOrder(String orderNo, Long userId);
}
