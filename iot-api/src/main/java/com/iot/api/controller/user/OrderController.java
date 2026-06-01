package com.iot.api.controller.user;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.dto.request.PayOrderRequest;
import com.iot.core.dto.response.OrderVO;
import com.iot.core.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端订单控制器
 * <p>
 * 提供订单列表查询、详情查看、支付和退款接口。
 * 所有接口需要 JWT 认证。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 获取用户订单列表（分页 + 筛选）
     * <p>
     * 支持按订单状态、时间范围筛选。
     * </p>
     *
     * @param page        页码，默认1
     * @param size        每页数量，默认20
     * @param orderStatus 订单状态（可选）
     * @param startTime   开始时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime     结束时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @return 分页订单列表
     */
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[订单列表] userId: {}, page: {}, size: {}, status: {}", userId, page, size, orderStatus);

        // 简单时间解析（实际应用中使用 @DateTimeFormat 或自定义转换器）
        java.time.LocalDateTime start = startTime != null
                ? java.time.LocalDateTime.parse(startTime.replace("T", " ").substring(0, 19)
                        .replace("T", " "))
                : null;
        java.time.LocalDateTime end = endTime != null
                ? java.time.LocalDateTime.parse(endTime.replace("T", " ").substring(0, 19)
                        .replace("T", " "))
                : null;

        PageResult<OrderVO> result = orderService.listOrders(
                userId, page, size, orderStatus, start, end);
        return Result.success(result);
    }

    /**
     * 获取订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[订单详情] userId: {}, orderId: {}", userId, id);

        OrderVO order = orderService.getOrderDetail(id, userId);
        return Result.success(order);
    }

    /**
     * 模拟支付
     * <p>
     * 仅支持已完成（COMPLETED）且未支付（UNPAID）的订单。
     * </p>
     *
     * @param request 支付请求（orderNo）
     * @return 操作结果
     */
    @PostMapping("/pay")
    public Result<String> payOrder(@RequestBody @Valid PayOrderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[支付] userId: {}, orderNo: {}", userId, request.getOrderNo());

        orderService.payOrder(request.getOrderNo(), userId);
        return Result.success("支付成功");
    }

    /**
     * 申请退款
     * <p>
     * 仅支持已支付（PAID）的订单。模拟退款，直接更新状态。
     * </p>
     *
     * @param request 退款请求（orderNo）
     * @return 操作结果
     */
    @PostMapping("/refund")
    public Result<String> refundOrder(@RequestBody @Valid PayOrderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[退款] userId: {}, orderNo: {}", userId, request.getOrderNo());

        orderService.refundOrder(request.getOrderNo(), userId);
        return Result.success("退款成功");
    }
}
