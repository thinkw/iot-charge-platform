package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.dto.response.OrderVO;
import com.iot.core.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 运营后台 - 订单管理控制器
 * <p>
 * 提供管理端订单查询、手动结束异常订单、退款等接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * 分页查询全量订单列表
     * <p>
     * 运营管理员可查看所有用户的订单，支持多条件筛选。
     * </p>
     *
     * @param page        页码，默认1
     * @param size        每页数量，默认20
     * @param userId      用户ID（可选）
     * @param chargerId   充电桩ID（可选）
     * @param stationId   充电站ID（可选）
     * @param orderStatus 订单状态（可选）
     * @param payStatus   支付状态（可选）
     * @param startTime   开始时间（可选）
     * @param endTime     结束时间（可选）
     * @return 分页订单列表
     */
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long chargerId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) Integer payStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-订单列表] 操作人: {}, page: {}, size: {}", operatorId, page, size);

        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        PageResult<OrderVO> result = orderService.listAllOrders(
                userId, chargerId, stationId, orderStatus, payStatus, start, end, page, size);
        return Result.success(result);
    }

    /**
     * 获取订单详情
     * <p>
     * 管理员可查看任意订单，不受 userId 限制。
     * 复用用户端 OrderService.getOrderDetail，但不校验 userId。
     * </p>
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-订单详情] 操作人: {}, orderId: {}", operatorId, id);

        // 管理端查看订单详情，userId 传 null 绕过归属校验
        // 复用 getOrderDetail，但需要管理员权限才能访问
        OrderVO order = orderService.getOrderDetail(id, null);
        return Result.success(order);
    }

    /**
     * 手动结束异常订单
     * <p>
     * 用于处理因设备离线、系统异常等原因未正常结束的充电订单。
     * 操作后订单状态变为"已完成"，充电桩恢复为"空闲"。
     * </p>
     *
     * @param request 请求参数（orderNo, reason）
     * @return 操作结果
     */
    @PostMapping("/end")
    public Result<String> forceEndOrder(@RequestBody ForceEndRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-手动结束] 操作人: {}, orderNo: {}, 原因: {}", operatorId,
                request.getOrderNo(), request.getReason());

        orderService.forceEndOrder(request.getOrderNo(), operatorId, request.getReason());
        return Result.success("订单已手动结束");
    }

    /**
     * 管理员退款
     * <p>
     * 允许管理员直接对已支付订单进行退款操作。
     * </p>
     *
     * @param request 请求参数（orderNo, reason）
     * @return 操作结果
     */
    @PostMapping("/refund")
    public Result<String> refundOrder(@RequestBody RefundRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-退款] 操作人: {}, orderNo: {}, 原因: {}", operatorId,
                request.getOrderNo(), request.getReason());

        orderService.adminRefundOrder(request.getOrderNo(), operatorId, request.getReason());
        return Result.success("退款成功");
    }

    // ==================== 内部请求类 ====================

    /**
     * 手动结束异常订单请求
     */
    @Data
    public static class ForceEndRequest {
        /** 订单编号 */
        @NotBlank(message = "订单编号不能为空")
        private String orderNo;

        /** 手动结束原因 */
        @NotBlank(message = "结束原因不能为空")
        private String reason;
    }

    /**
     * 管理员退款请求
     */
    @Data
    public static class RefundRequest {
        /** 订单编号 */
        @NotBlank(message = "订单编号不能为空")
        private String orderNo;

        /** 退款原因 */
        @NotBlank(message = "退款原因不能为空")
        private String reason;
    }

    // ==================== 工具方法 ====================

    /**
     * 简单时间字符串解析
     * <p>
     * 支持格式：yyyy-MM-dd HH:mm:ss 和 ISO 格式（含T）。
     * </p>
     */
    private LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            String cleaned = timeStr.replace("T", " ").trim();
            if (cleaned.length() >= 19) {
                cleaned = cleaned.substring(0, 19);
            }
            return LocalDateTime.parse(cleaned, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("[时间解析] 无效的时间格式: {}", timeStr);
            return null;
        }
    }
}
