package com.iot.api.controller.user;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.Result;
import com.iot.core.dto.request.ChargeStartRequest;
import com.iot.core.dto.request.ChargeStopRequest;
import com.iot.core.dto.response.ChargeStatusVO;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端充电控制器
 * <p>
 * 提供扫码启桩、结束充电和实时状态查询接口。
 * 所有接口需要 JWT 认证。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/charge")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;

    /**
     * 扫码启桩
     * <p>
     * 用户扫描充电桩二维码后发起充电请求。
     * 返回订单信息，用户可通过 WebSocket 订阅充电进度。
     * </p>
     *
     * @param request 启桩请求（chargerId）
     * @return 充电订单
     */
    @PostMapping("/start")
    public Result<String> startCharge(@RequestBody @Valid ChargeStartRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[扫码启桩] userId: {}, chargerId: {}", userId, request.getChargerId());

        ChargeOrder order = chargeService.startCharge(userId, request.getChargerId());
        return Result.success(order.getOrderNo());
    }

    /**
     * 结束充电
     * <p>
     * 停止充电，系统自动计算费用并生成最终订单。
     * </p>
     *
     * @param request 停桩请求（orderNo）
     * @return 操作结果
     */
    @PostMapping("/stop")
    public Result<String> stopCharge(@RequestBody @Valid ChargeStopRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[结束充电] userId: {}, orderNo: {}", userId, request.getOrderNo());

        ChargeOrder order = chargeService.stopCharge(userId, request.getOrderNo());
        return Result.success("充电已结束，总费用: " + order.getTotalAmount() + " 元");
    }

    /**
     * 获取充电实时状态
     * <p>
     * 包含实时电压、电流、功率、已充电量、估算费用和已充时长。
     * 用户端可定时轮询此接口或通过 WebSocket 接收实时推送。
     * </p>
     *
     * @param orderId 订单编号（路径变量名为 orderId，实际为 orderNo）
     * @return 充电实时状态
     */
    @GetMapping("/status/{orderId}")
    public Result<ChargeStatusVO> getChargeStatus(@PathVariable String orderId) {
        log.info("[充电状态] orderNo: {}", orderId);
        ChargeStatusVO status = chargeService.getChargeStatus(orderId);
        return Result.success(status);
    }
}
