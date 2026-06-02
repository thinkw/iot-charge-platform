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

    // ==================== 管理端接口 ====================

    /**
     * 管理端：分页查询全量订单列表
     * <p>
     * 运营管理端使用，不限制 userId，支持按用户ID、订单状态、支付状态、
     * 充电桩ID、充电站ID、时间范围等多条件筛选。
     * </p>
     *
     * @param userId      用户ID（可选）
     * @param chargerId   充电桩ID（可选）
     * @param stationId   充电站ID（可选）
     * @param orderStatus 订单状态（可选）
     * @param payStatus   支付状态（可选）
     * @param startTime   开始时间（可选）
     * @param endTime     结束时间（可选）
     * @param page        页码
     * @param size        每页数量
     * @return 分页订单列表
     */
    PageResult<OrderVO> listAllOrders(Long userId, Long chargerId, Long stationId,
                                        Integer orderStatus, Integer payStatus,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        int page, int size);

    /**
     * 管理端：手动结束异常订单
     * <p>
     * 运营管理员手动终止状态异常的充电订单（如设备离线后未自动结束的订单）。
     * 1. 校验订单状态为 CHARGING 或 ABNORMAL
     * 2. 下发停止指令（best-effort）
     * 3. 使用当前时间作为结束时间计算费用
     * 4. 更新订单状态为 COMPLETED
     * 5. 更新充电桩状态为 IDLE
     * </p>
     *
     * @param orderNo    订单编号
     * @param operatorId 操作人ID（管理员）
     * @param reason     手动结束原因
     */
    void forceEndOrder(String orderNo, Long operatorId, String reason);

    /**
     * 管理端：管理员退款
     * <p>
     * 允许管理员直接对已支付订单进行退款操作。
     * 与用户端退款不同的是，管理员退款可以使用乐观锁+降低限制。
     * 1. 校验 payStatus=PAID 或 REFUNDED
     * 2. 更新 payStatus=REFUNDED
     * </p>
     *
     * @param orderNo    订单编号
     * @param operatorId 操作人ID（管理员）
     * @param reason     退款原因
     */
    void adminRefundOrder(String orderNo, Long operatorId, String reason);
}
