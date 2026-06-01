package com.iot.api.controller.user;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.Result;
import com.iot.core.dto.request.CreateReservationRequest;
import com.iot.core.dto.request.PayOrderRequest;
import com.iot.core.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端预约控制器
 * <p>
 * 提供充电桩预约的创建和取消接口。
 * 所有接口需要 JWT 认证。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 创建预约订单
     * <p>
     * 选择充电桩和预约时段，系统检查时段冲突后创建预约。
     * 预约时段最早可提前 7 天。
     * </p>
     *
     * @param request 预约请求（chargerId, reserveDate, startTime, endTime）
     * @return 预约订单编号
     */
    @PostMapping("/create")
    public Result<String> createReservation(@RequestBody @Valid CreateReservationRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[创建预约] userId: {}, chargerId: {}, date: {}",
                userId, request.getChargerId(), request.getReserveDate());

        String orderNo = reservationService.createReservation(userId, request);
        return Result.success(orderNo);
    }

    /**
     * 取消预约
     * <p>
     * 仅支持待使用状态的预约，取消后押金退还。
     * </p>
     *
     * @param request 取消预约请求（orderNo）
     * @return 操作结果
     */
    @PostMapping("/cancel")
    public Result<String> cancelReservation(@RequestBody @Valid PayOrderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[取消预约] userId: {}, orderNo: {}", userId, request.getOrderNo());

        reservationService.cancelReservation(request.getOrderNo(), userId);
        return Result.success("预约已取消");
    }
}
