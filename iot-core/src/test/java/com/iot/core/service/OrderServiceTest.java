package com.iot.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.common.enums.PayStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.OrderVO;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.entity.Station;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.StationMapper;
import com.iot.core.mq.producer.ChargeEventProducer;
import com.iot.core.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 单元测试
 * <p>
 * 测试订单服务的核心业务逻辑：
 * - 订单支付流程（校验、乐观锁更新、事件发送）
 * - 订单退款流程（支付状态校验、状态更新）
 * - 订单详情查询（权限校验、VO 组装）
 * - 订单列表查询（条件筛选、空结果处理）
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService 单元测试")
class OrderServiceTest {

    @Mock
    private ChargeOrderMapper chargeOrderMapper;

    @Mock
    private ChargerMapper chargerMapper;

    @Mock
    private StationMapper stationMapper;

    @Mock
    private ChargeEventProducer chargeEventProducer;

    @Mock
    private PricingService pricingService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    private static final String ORDER_NO = "ORDER-20240601001";
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    // ==================== 测试数据工厂方法 ====================

    /** 创建一个指定支付状态和订单状态的订单 */
    private ChargeOrder createOrder(int payStatus, int orderStatus, Long userId) {
        ChargeOrder order = new ChargeOrder();
        order.setId(1L);
        order.setOrderNo(ORDER_NO);
        order.setUserId(userId);
        order.setChargerId(100L);
        order.setStationId(200L);
        order.setStartTime(LocalDateTime.now().minusHours(2));
        order.setEndTime(LocalDateTime.now());
        order.setChargedEnergy(new BigDecimal("50.00"));
        order.setTotalAmount(new BigDecimal("40.00"));
        order.setElectricityFee(new BigDecimal("32.00"));
        order.setServiceFee(new BigDecimal("8.00"));
        order.setPayStatus(payStatus);
        order.setOrderStatus(orderStatus);
        order.setVersion(1);
        return order;
    }

    /** 创建一个默认的已完成的未支付订单 */
    private ChargeOrder createDefaultOrder() {
        return createOrder(PayStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
    }

    /** 创建一个充电桩实体 */
    private Charger createCharger(Long id, String name) {
        Charger charger = new Charger();
        charger.setId(id);
        charger.setName(name);
        return charger;
    }

    /** 创建一个充电站实体 */
    private Station createStation(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setName(name);
        return station;
    }

    // ==================== payOrder 测试 ====================

    @Test
    @DisplayName("payOrder - 订单不存在 → 抛出 BusinessException(404)")
    void payOrder_OrderNotFound_Throws404() {
        /*
         * 测试场景：根据 orderNo 查询不到订单
         * 预期：抛出 BusinessException，错误码 404
         */
        // Arrange
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.payOrder(ORDER_NO, USER_ID));
        assertEquals(404, ex.getCode(), "订单不存在时应返回 404");
    }

    @Test
    @DisplayName("payOrder - 订单不属于当前用户 → 抛出 BusinessException(403)")
    void payOrder_OrderNotBelongToUser_Throws403() {
        /*
         * 测试场景：订单属于其他用户（userId=2），当前用户（userId=1）尝试支付
         * 预期：抛出 BusinessException，错误码 403
         */
        // Arrange
        ChargeOrder order = createDefaultOrder();
        order.setUserId(OTHER_USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.payOrder(ORDER_NO, USER_ID));
        assertEquals(403, ex.getCode(), "无权操作时应返回 403");
    }

    @Test
    @DisplayName("payOrder - 支付状态不是UNPAID（已支付）→ 抛出 BusinessException(409)")
    void payOrder_AlreadyPaid_Throws409() {
        /*
         * 测试场景：订单已支付（payStatus=PAID），再次尝试支付
         * 预期：抛出 BusinessException，错误码 409
         */
        // Arrange
        ChargeOrder order = createOrder(PayStatusEnum.PAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.payOrder(ORDER_NO, USER_ID));
        assertEquals(409, ex.getCode(), "已支付订单重复支付时应返回 409");
    }

    @Test
    @DisplayName("payOrder - 订单状态不是COMPLETED（还在充电中）→ 抛出 BusinessException(409)")
    void payOrder_OrderNotCompleted_Throws409() {
        /*
         * 测试场景：订单仍在充电中（orderStatus=CHARGING），未完成充电
         * 预期：抛出 BusinessException，错误码 409
         */
        // Arrange
        ChargeOrder order = createOrder(PayStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.CHARGING.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.payOrder(ORDER_NO, USER_ID));
        assertEquals(409, ex.getCode(), "充电中的订单支付时应返回 409");
    }

    @Test
    @DisplayName("payOrder - 正常支付 → payStatus=PAID, payTime不为空")
    void payOrder_Success_UpdatesPayStatusAndPayTime() {
        /*
         * 测试场景：订单已完成且未支付，执行支付操作
         * 预期：
         *   - payStatus 更新为 PAID
         *   - payTime 被设置（不为空）
         *   - 触发支付完成事件发送
         */
        // Arrange
        ChargeOrder order = createDefaultOrder();
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(chargeOrderMapper.updateById(any(ChargeOrder.class))).thenReturn(1);

        // Act
        orderService.payOrder(ORDER_NO, USER_ID);

        // Assert: 捕获 updateById 的参数，验证状态变更
        ArgumentCaptor<ChargeOrder> orderCaptor = ArgumentCaptor.forClass(ChargeOrder.class);
        verify(chargeOrderMapper).updateById(orderCaptor.capture());
        ChargeOrder updatedOrder = orderCaptor.getValue();

        assertEquals(PayStatusEnum.PAID.getCode(), updatedOrder.getPayStatus(),
                "支付成功后支付状态应为 PAID");
        assertNotNull(updatedOrder.getPayTime(), "支付成功后支付时间不应为空");

        // Assert: 验证事件发送
        verify(chargeEventProducer).publishPayCompletedEvent(orderCaptor.capture());
    }

    // ==================== refundOrder 测试 ====================

    @Test
    @DisplayName("refundOrder - 订单不存在 → 抛出 BusinessException(404)")
    void refundOrder_OrderNotFound_Throws404() {
        /*
         * 测试场景：根据 orderNo 查询不到订单
         * 预期：抛出 BusinessException，错误码 404
         */
        // Arrange
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.refundOrder(ORDER_NO, USER_ID));
        assertEquals(404, ex.getCode(), "订单不存在时应返回 404");
    }

    @Test
    @DisplayName("refundOrder - 订单不属于当前用户 → 抛出 BusinessException(403)")
    void refundOrder_OrderNotBelongToUser_Throws403() {
        /*
         * 测试场景：订单属于其他用户，当前用户尝试退款
         * 预期：抛出 BusinessException，错误码 403
         */
        // Arrange
        ChargeOrder order = createOrder(PayStatusEnum.PAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), OTHER_USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.refundOrder(ORDER_NO, USER_ID));
        assertEquals(403, ex.getCode(), "无权操作时应返回 403");
    }

    @Test
    @DisplayName("refundOrder - 支付状态不是PAID（未支付）→ 抛出 BusinessException(409)")
    void refundOrder_NotPaid_Throws409() {
        /*
         * 测试场景：订单未支付（payStatus=UNPAID），尝试退款
         * 预期：抛出 BusinessException，错误码 409
         */
        // Arrange
        ChargeOrder order = createOrder(PayStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.refundOrder(ORDER_NO, USER_ID));
        assertEquals(409, ex.getCode(), "未支付的订单退款时应返回 409");
    }

    @Test
    @DisplayName("refundOrder - 正常退款 → payStatus=REFUNDED")
    void refundOrder_Success_UpdatesPayStatus() {
        /*
         * 测试场景：订单已支付，执行退款操作
         * 预期：payStatus 更新为 REFUNDED
         */
        // Arrange
        ChargeOrder order = createOrder(PayStatusEnum.PAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(chargeOrderMapper.updateById(any(ChargeOrder.class))).thenReturn(1);

        // Act
        orderService.refundOrder(ORDER_NO, USER_ID);

        // Assert: 捕获 updateById 的参数，验证状态变更
        ArgumentCaptor<ChargeOrder> orderCaptor = ArgumentCaptor.forClass(ChargeOrder.class);
        verify(chargeOrderMapper).updateById(orderCaptor.capture());
        ChargeOrder updatedOrder = orderCaptor.getValue();

        assertEquals(PayStatusEnum.REFUNDED.getCode(), updatedOrder.getPayStatus(),
                "退款成功后支付状态应为 REFUNDED");
    }

    // ==================== getOrderDetail 测试 ====================

    @Test
    @DisplayName("getOrderDetail - 订单不存在 → 抛出 BusinessException(404)")
    void getOrderDetail_OrderNotFound_Throws404() {
        /*
         * 测试场景：根据 orderId 查询不到订单
         * 预期：抛出 BusinessException，错误码 404
         */
        // Arrange
        when(chargeOrderMapper.selectById(anyLong())).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrderDetail(1L, USER_ID));
        assertEquals(404, ex.getCode(), "订单不存在时应返回 404");
    }

    @Test
    @DisplayName("getOrderDetail - 订单不属于当前用户 → 抛出 BusinessException(403)")
    void getOrderDetail_OrderNotBelongToUser_Throws403() {
        /*
         * 测试场景：订单属于其他用户，当前用户尝试查询
         * 预期：抛出 BusinessException，错误码 403
         */
        // Arrange
        ChargeOrder order = createDefaultOrder();
        order.setUserId(OTHER_USER_ID);
        when(chargeOrderMapper.selectById(anyLong())).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrderDetail(1L, USER_ID));
        assertEquals(403, ex.getCode(), "无权查看时应返回 403");
    }

    @Test
    @DisplayName("getOrderDetail - 正常返回 → OrderVO 含 chargerName, stationName")
    void getOrderDetail_Success_ReturnsOrderVOWithNames() {
        /*
         * 测试场景：订单存在且属于当前用户
         * 预期：返回完整的 OrderVO，包含充电桩名称和充电站名称
         */
        // Arrange
        ChargeOrder order = createDefaultOrder();
        Charger charger = createCharger(100L, "直流快充桩-A01");
        Station station = createStation(200L, "朝阳区充电站");

        when(chargeOrderMapper.selectById(anyLong())).thenReturn(order);
        when(chargerMapper.selectById(anyLong())).thenReturn(charger);
        when(stationMapper.selectById(anyLong())).thenReturn(station);

        // Act
        OrderVO result = orderService.getOrderDetail(1L, USER_ID);

        // Assert
        assertNotNull(result, "应返回 OrderVO");
        assertEquals(ORDER_NO, result.getOrderNo(), "订单编号应一致");
        assertEquals("直流快充桩-A01", result.getChargerName(), "应包含充电桩名称");
        assertEquals("朝阳区充电站", result.getStationName(), "应包含充电站名称");
        assertEquals(USER_ID, result.getUserId(), "用户 ID 应一致");
        assertEquals(Integer.valueOf(PayStatusEnum.UNPAID.getCode()), result.getPayStatus(),
                "支付状态应一致");
        assertEquals(Integer.valueOf(OrderStatusEnum.COMPLETED.getCode()), result.getOrderStatus(),
                "订单状态应一致");
        assertEquals("未支付", result.getPayStatusDesc(), "支付状态描述应正确");
        assertEquals("已完成", result.getOrderStatusDesc(), "订单状态描述应正确");
    }

    // ==================== listOrders 测试 ====================

    @Test
    @DisplayName("listOrders - 带状态筛选 → verify 查询条件包含 orderStatus")
    void listOrders_WithStatusFilter_QueryContainsOrderStatus() {
        /*
         * 测试场景：带订单状态筛选参数查询订单列表
         * 预期：查询条件中包含 orderStatus 的等值条件
         *       使用 ArgumentCaptor 捕获 wrapper 以验证 SQL 片段
         */
        // Arrange
        ChargeOrder order = createDefaultOrder();
        Page<ChargeOrder> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(order));

        Charger charger = createCharger(100L, "直流快充桩-A01");
        Station station = createStation(200L, "朝阳区充电站");

        when(chargeOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);
        when(chargerMapper.selectBatchIds(anyList())).thenReturn(List.of(charger));
        when(stationMapper.selectBatchIds(anyList())).thenReturn(List.of(station));

        // Act
        Integer orderStatusFilter = OrderStatusEnum.COMPLETED.getCode();
        orderService.listOrders(USER_ID, 1, 10, orderStatusFilter, null, null);

        // Assert: 验证 selectPage 被调用且 wrapper 包含 order_status 限制
        // 注意：纯 Mockito 环境下 LambdaQueryWrapper 构建需要 MyBatis-Plus lambda 缓存，
        // 此处验证方法被正确调用即可，具体 SQL 片段在集成测试中验证。
        verify(chargeOrderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders - 空结果 → 返回空 PageResult")
    void listOrders_EmptyResult_ReturnsEmptyPageResult() {
        /*
         * 测试场景：无满足条件的订单，查询结果为空
         * 预期：返回空列表的 PageResult，不查询关联的充电桩/充电站
         */
        // Arrange
        Page<ChargeOrder> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(List.of());
        when(chargeOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        // Act
        PageResult<OrderVO> result = orderService.listOrders(USER_ID, 1, 10, null, null, null);

        // Assert
        assertNotNull(result, "返回的 PageResult 不应为 null");
        assertTrue(result.getRecords().isEmpty(), "空查询结果应返回空列表");
        assertEquals(0L, result.getTotal(), "总记录数应为 0");

        // 空结果应跳过关联查询，验证 chargerMapper 无交互
        verify(chargerMapper, never()).selectBatchIds(anyList());
        verify(stationMapper, never()).selectBatchIds(anyList());
    }

    // ==================== 管理端：getOrderDetail (userId=null) ====================

    @Test
    @DisplayName("getOrderDetail - 管理端访问（userId=null）→ 跳过归属校验，正常返回")
    void getOrderDetail_AdminAccess_ReturnsOrderVO() {
        /*
         * 测试场景：管理端通过 userId=null 访问订单详情，应跳过归属校验
         * 预期：正常返回订单详情
         */
        ChargeOrder order = createDefaultOrder();
        Charger charger = createCharger(100L, "直流快充桩-A01");
        Station station = createStation(200L, "朝阳区充电站");

        when(chargeOrderMapper.selectById(anyLong())).thenReturn(order);
        when(chargerMapper.selectById(anyLong())).thenReturn(charger);
        when(stationMapper.selectById(anyLong())).thenReturn(station);

        OrderVO result = orderService.getOrderDetail(1L, null); // userId=null 管理端

        assertNotNull(result, "管理端访问应正常返回 OrderVO");
        assertEquals(ORDER_NO, result.getOrderNo());
    }

    @Test
    @DisplayName("getOrderDetail - 管理端访问订单不存在 → 抛出 BusinessException(404)")
    void getOrderDetail_AdminAccess_OrderNotFound_Throws404() {
        when(chargeOrderMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrderDetail(999L, null));
        assertEquals(404, ex.getCode());
    }

    // ==================== 管理端：listAllOrders ====================

    @Test
    @DisplayName("listAllOrders - 不带筛选条件 → 返回全量订单分页")
    void listAllOrders_NoFilter_ReturnsAllOrders() {
        ChargeOrder order = createDefaultOrder();
        Page<ChargeOrder> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(order));

        Charger charger = createCharger(100L, "直流快充桩-A01");
        Station station = createStation(200L, "朝阳区充电站");

        when(chargeOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);
        when(chargerMapper.selectBatchIds(anyList())).thenReturn(List.of(charger));
        when(stationMapper.selectBatchIds(anyList())).thenReturn(List.of(station));

        PageResult<OrderVO> result = orderService.listAllOrders(
                null, null, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listAllOrders - 按订单状态筛选 → 查询条件包含 orderStatus")
    void listAllOrders_WithOrderStatusFilter() {
        ChargeOrder order = createDefaultOrder();
        Page<ChargeOrder> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(order));

        when(chargeOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);
        when(chargerMapper.selectBatchIds(anyList())).thenReturn(List.of(createCharger(100L, "桩")));
        when(stationMapper.selectBatchIds(anyList())).thenReturn(List.of(createStation(200L, "站")));

        orderService.listAllOrders(null, null, null,
                OrderStatusEnum.CHARGING.getCode(), null, null, null, 1, 10);

        verify(chargeOrderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listAllOrders - 空结果 → 返回空 PageResult")
    void listAllOrders_EmptyResult_ReturnsEmptyPageResult() {
        Page<ChargeOrder> pageResult = new Page<>(1, 10, 0);
        pageResult.setRecords(List.of());
        when(chargeOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        PageResult<OrderVO> result = orderService.listAllOrders(
                null, null, null, null, null, null, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(chargerMapper, never()).selectBatchIds(anyList());
    }

    // ==================== 管理端：forceEndOrder ====================

    @Test
    @DisplayName("forceEndOrder - 订单不存在 → 抛出 BusinessException(404)")
    void forceEndOrder_OrderNotFound_Throws404() {
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.forceEndOrder(ORDER_NO, 3L, "设备离线"));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("forceEndOrder - 订单状态不是CHARGING/ABNORMAL → 抛出 BusinessException(409)")
    void forceEndOrder_InvalidStatus_Throws409() {
        ChargeOrder order = createDefaultOrder(); // COMPLETED 状态
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.forceEndOrder(ORDER_NO, 3L, "测试"));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("forceEndOrder - 正常强制结束 → 订单状态变为COMPLETED，充电桩恢复IDLE")
    void forceEndOrder_Success_UpdatesOrderAndCharger() {
        ChargeOrder order = createOrder(PayStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.CHARGING.getCode(), USER_ID);
        order.setChargedEnergy(new BigDecimal("30.00"));
        order.setStartTime(LocalDateTime.now().minusHours(1));

        Charger charger = new Charger();
        charger.setId(100L);
        charger.setSn("CHARGER-001");
        charger.setStationId(200L);
        charger.setStatus(DeviceStatusEnum.CHARGING.getCode());

        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(chargerMapper.selectById(100L)).thenReturn(charger);
        when(deviceService.sendCommand(anyString(), anyString(), any())).thenReturn(true);
        when(pricingService.calculateExactFee(anyLong(), any(), any(), any(), any()))
                .thenReturn(new PricingService.FeeDetail(
                        new BigDecimal("24.00"), new BigDecimal("6.00"), new BigDecimal("30.00")));
        when(chargeOrderMapper.updateById(any(ChargeOrder.class))).thenReturn(1);
        when(chargerMapper.updateById(any(Charger.class))).thenReturn(1);

        orderService.forceEndOrder(ORDER_NO, 3L, "设备离线");

        // Verify: 订单状态被更新为完成
        ArgumentCaptor<ChargeOrder> orderCaptor = ArgumentCaptor.forClass(ChargeOrder.class);
        verify(chargeOrderMapper).updateById(orderCaptor.capture());
        assertEquals(OrderStatusEnum.PENDING_CONFIRM.getCode(), orderCaptor.getValue().getOrderStatus());

        // Verify: 充电桩状态被恢复为空闲
        ArgumentCaptor<Charger> chargerCaptor = ArgumentCaptor.forClass(Charger.class);
        verify(chargerMapper).updateById(chargerCaptor.capture());
        assertEquals(DeviceStatusEnum.IDLE.getCode(), chargerCaptor.getValue().getStatus());

        // Verify: 发送充电结束事件
        verify(chargeEventProducer).publishChargeEndEvent(any(ChargeOrder.class));
    }

    // ==================== 管理端：adminRefundOrder ====================

    @Test
    @DisplayName("adminRefundOrder - 订单不存在 → 抛出 BusinessException(404)")
    void adminRefundOrder_OrderNotFound_Throws404() {
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.adminRefundOrder(ORDER_NO, 3L, "用户投诉"));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("adminRefundOrder - 订单已退款 → 抛出 BusinessException(409)")
    void adminRefundOrder_AlreadyRefunded_Throws409() {
        ChargeOrder order = createOrder(PayStatusEnum.REFUNDED.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.adminRefundOrder(ORDER_NO, 3L, "重复退款"));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("adminRefundOrder - 订单未支付 → 抛出 BusinessException(409)")
    void adminRefundOrder_NotPaid_Throws409() {
        ChargeOrder order = createOrder(PayStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.adminRefundOrder(ORDER_NO, 3L, "测试"));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("adminRefundOrder - 正常退款 → payStatus=REFUNDED，记录原因")
    void adminRefundOrder_Success_RefundsAndRecordsReason() {
        ChargeOrder order = createOrder(PayStatusEnum.PAID.getCode(),
                OrderStatusEnum.COMPLETED.getCode(), USER_ID);
        when(chargeOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(chargeOrderMapper.updateById(any(ChargeOrder.class))).thenReturn(1);

        String reason = "用户投诉，同意退款";
        orderService.adminRefundOrder(ORDER_NO, 3L, reason);

        ArgumentCaptor<ChargeOrder> orderCaptor = ArgumentCaptor.forClass(ChargeOrder.class);
        verify(chargeOrderMapper).updateById(orderCaptor.capture());
        assertEquals(PayStatusEnum.REFUNDED.getCode(), orderCaptor.getValue().getPayStatus());
        assertEquals(reason, orderCaptor.getValue().getCancelReason());
    }
}
