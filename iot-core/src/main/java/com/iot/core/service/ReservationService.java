package com.iot.core.service;

import com.iot.core.dto.request.CreateReservationRequest;

/**
 * 预约服务接口
 * <p>
 * 提供充电桩预约的创建和取消功能。
 * 使用 Redis 分布式锁锁定预约时段，防止时段冲突。
 * </p>
 *
 * @author IoT Team
 */
public interface ReservationService {

    /**
     * 创建预约订单
     * <p>
     * 1. 校验充电桩存在且状态可用
     * 2. 检查时段冲突（同一充电桩在目标时段内无其他活跃预约）
     * 3. 使用 Redis 分布式锁锁定该时段
     * 4. 创建 ReservationOrder（押金暂免）
     * 5. 返回预约编号
     * </p>
     *
     * @param userId  用户ID
     * @param request 预约请求
     * @return 预约订单编号
     */
    String createReservation(Long userId, CreateReservationRequest request);

    /**
     * 取消预约
     * <p>
     * 1. 校验预约订单属于当前用户
     * 2. 校验 status=0（待使用）
     * 3. 更新预约状态为已取消
     * 4. 退还押金（如已支付）
     * </p>
     *
     * @param orderNo 预约编号
     * @param userId  用户ID
     */
    void cancelReservation(String orderNo, Long userId);
}
