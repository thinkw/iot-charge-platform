package com.iot.core.service;

import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.core.dto.response.ChargeStatusVO;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mq.producer.ChargeEventProducer;
import com.iot.core.service.impl.ChargeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChargeServiceImpl 单元测试
 * <p>
 * 覆盖扫码启桩、结束充电、实时状态查询三大核心流程，
 * 使用 Mockito 隔离 ChargerMapper / ChargeOrderMapper / DeviceService /
 * PricingService / RedissonClient / RedisTemplate / ChargeEventProducer 等外部依赖。
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChargeService 单元测试")
class ChargeServiceTest {

    @Mock private ChargerMapper chargerMapper;
    @Mock private ChargeOrderMapper chargeOrderMapper;
    @Mock private DeviceService deviceService;
    @Mock private PricingService pricingService;
    @Mock private RedissonClient redissonClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ChargeEventProducer chargeEventProducer;

    @Mock private RLock lock;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private ChargeServiceImpl chargeService;

    private static final Long USER_ID = 1L;
    private static final Long ANOTHER_USER_ID = 2L;
    private static final Long CHARGER_ID = 1L;
    private static final Long STATION_ID = 1L;
    private static final String SN = "CHARGER-001";
    private static final String ORDER_NO = "C202501010001001";

    private Charger idleCharger;
    private Charger chargingCharger;
    private Charger faultCharger;
    private ChargeOrder chargingOrder;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 通用 Mock 配置（lenient 避免不必要的桩报错）
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        // 空闲充电桩
        idleCharger = new Charger();
        idleCharger.setId(CHARGER_ID);
        idleCharger.setSn(SN);
        idleCharger.setStationId(STATION_ID);
        idleCharger.setStatus(DeviceStatusEnum.IDLE.getCode());
        idleCharger.setChargedEnergy(BigDecimal.ZERO);

        // 充电中充电桩
        chargingCharger = new Charger();
        chargingCharger.setId(CHARGER_ID);
        chargingCharger.setSn(SN);
        chargingCharger.setStationId(STATION_ID);
        chargingCharger.setStatus(DeviceStatusEnum.CHARGING.getCode());

        // 故障充电桩
        faultCharger = new Charger();
        faultCharger.setId(CHARGER_ID);
        faultCharger.setSn(SN);
        faultCharger.setStationId(STATION_ID);
        faultCharger.setStatus(DeviceStatusEnum.FAULT.getCode());

        // 充电中订单
        chargingOrder = new ChargeOrder();
        chargingOrder.setOrderNo(ORDER_NO);
        chargingOrder.setUserId(USER_ID);
        chargingOrder.setChargerId(CHARGER_ID);
        chargingOrder.setStationId(STATION_ID);
        chargingOrder.setStartTime(LocalDateTime.now().minusHours(1));
        chargingOrder.setOrderStatus(OrderStatusEnum.CHARGING.getCode());
        chargingOrder.setPayStatus(0);
        chargingOrder.setChargedEnergy(new BigDecimal("10.5"));
    }

    // ==================== startCharge 扫码启桩 ====================

    @Nested
    @DisplayName("startCharge 扫码启桩测试")
    class StartChargeTest {

        @Test
        @DisplayName("充电桩不存在 → 抛出 BusinessException(404)")
        void startCharge_chargerNotFound() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.startCharge(USER_ID, CHARGER_ID));

            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("充电桩状态为故障(FAULT=3) → 抛出 BusinessException(409)")
        void startCharge_chargerFault() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(faultCharger);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.startCharge(USER_ID, CHARGER_ID));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("充电桩状态为充电中(CHARGING=2) → 抛出 BusinessException(409)")
        void startCharge_chargerCharging() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(chargingCharger);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.startCharge(USER_ID, CHARGER_ID));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("分布式锁获取失败 → 抛出 BusinessException(429)")
        void startCharge_lockFailed() throws InterruptedException {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.startCharge(USER_ID, CHARGER_ID));

            assertEquals(429, ex.getCode());
        }

        @Test
        @DisplayName("锁内二次校验状态已变更 → 抛出 BusinessException(409)")
        void startCharge_statusChangedInLock() {
            // 第一次 selectById（锁外）返回空闲；第二次 selectById（锁内二次校验）返回充电中
            when(chargerMapper.selectById(CHARGER_ID))
                    .thenReturn(idleCharger)
                    .thenReturn(chargingCharger);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.startCharge(USER_ID, CHARGER_ID));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("正常启桩（IDLE状态）→ 返回 ChargeOrder，orderStatus=CHARGING")
        void startCharge_success() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(deviceService.sendCommand(anyString(), anyString(), anyMap())).thenReturn(true);
            when(chargeOrderMapper.insert(any(ChargeOrder.class))).thenReturn(1);

            ChargeOrder result = chargeService.startCharge(USER_ID, CHARGER_ID);

            assertNotNull(result);
            assertNotNull(result.getOrderNo());
            assertEquals(USER_ID, result.getUserId());
            assertEquals(CHARGER_ID, result.getChargerId());
            assertEquals(OrderStatusEnum.CHARGING.getCode(), result.getOrderStatus());
            assertNotNull(result.getStartTime());
        }

        @Test
        @DisplayName("正常启桩 → verify 调用 deviceService.sendCommand('START_CHARGE')")
        void startCharge_verifySendCommand() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(deviceService.sendCommand(anyString(), anyString(), anyMap())).thenReturn(true);
            when(chargeOrderMapper.insert(any(ChargeOrder.class))).thenReturn(1);

            chargeService.startCharge(USER_ID, CHARGER_ID);

            verify(deviceService).sendCommand(eq(SN), eq("START_CHARGE"), anyMap());
        }

        @Test
        @DisplayName("正常启桩 → verify 调用 chargeEventProducer.publishChargeStartEvent()")
        void startCharge_verifyPublishEvent() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(deviceService.sendCommand(anyString(), anyString(), anyMap())).thenReturn(true);
            when(chargeOrderMapper.insert(any(ChargeOrder.class))).thenReturn(1);

            chargeService.startCharge(USER_ID, CHARGER_ID);

            verify(chargeEventProducer).publishChargeStartEvent(any(ChargeOrder.class));
        }

        @Test
        @DisplayName("MQTT指令下发失败（sendCommand返回false）→ 不抛异常，订单仍创建成功")
        void startCharge_commandFailed_stillSucceeds() {
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(deviceService.sendCommand(anyString(), anyString(), anyMap())).thenReturn(false);
            when(chargeOrderMapper.insert(any(ChargeOrder.class))).thenReturn(1);

            assertDoesNotThrow(() -> {
                ChargeOrder result = chargeService.startCharge(USER_ID, CHARGER_ID);
                assertNotNull(result);
                assertEquals(OrderStatusEnum.CHARGING.getCode(), result.getOrderStatus());
            });
        }
    }

    // ==================== stopCharge 结束充电 ====================

    @Nested
    @DisplayName("stopCharge 结束充电测试")
    class StopChargeTest {

        @Test
        @DisplayName("订单不存在 → 抛出 BusinessException(404)")
        void stopCharge_orderNotFound() {
            when(chargeOrderMapper.selectOne(any())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.stopCharge(USER_ID, ORDER_NO));

            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("订单不属于当前用户 → 抛出 BusinessException(403)")
        void stopCharge_orderNotBelongToUser() {
            when(chargeOrderMapper.selectOne(any())).thenReturn(chargingOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.stopCharge(ANOTHER_USER_ID, ORDER_NO));

            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("订单状态不是CHARGING → 抛出 BusinessException(409)")
        void stopCharge_orderNotCharging() {
            chargingOrder.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
            when(chargeOrderMapper.selectOne(any())).thenReturn(chargingOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.stopCharge(USER_ID, ORDER_NO));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("正常停桩 → 订单状态变为COMPLETED，费用已计算")
        void stopCharge_success() {
            mockStopChargeSuccessFlow();

            ChargeOrder result = chargeService.stopCharge(USER_ID, ORDER_NO);

            assertEquals(OrderStatusEnum.COMPLETED.getCode(), result.getOrderStatus());
            assertEquals(new BigDecimal("15.5"), result.getChargedEnergy());
            assertEquals(new BigDecimal("10.00"), result.getElectricityFee());
            assertEquals(new BigDecimal("5.00"), result.getServiceFee());
            assertEquals(new BigDecimal("15.00"), result.getTotalAmount());
            assertNotNull(result.getEndTime());
        }

        @Test
        @DisplayName("正常停桩 → verify 调用 pricingService.calculateExactFee()")
        void stopCharge_verifyCalculateFee() {
            mockStopChargeSuccessFlow();

            chargeService.stopCharge(USER_ID, ORDER_NO);

            verify(pricingService).calculateExactFee(
                    eq(STATION_ID), any(LocalDateTime.class), any(LocalDateTime.class), any(BigDecimal.class));
        }

        @Test
        @DisplayName("正常停桩 → 充电桩状态恢复为IDLE")
        void stopCharge_verifyChargerStatusRestored() {
            mockStopChargeSuccessFlow();

            chargeService.stopCharge(USER_ID, ORDER_NO);

            // 验证 chargerMapper.updateById 收到的 Charger 状态为 IDLE
            verify(chargerMapper).updateById(ArgumentMatchers.<Charger>argThat(c ->
                    c.getStatus() == DeviceStatusEnum.IDLE.getCode()));
        }

        /**
         * 设置 stopCharge 成功流程所需的全部 Mock 行为（复用）
         */
        private void mockStopChargeSuccessFlow() {
            when(chargeOrderMapper.selectOne(any())).thenReturn(chargingOrder);
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(deviceService.sendCommand(anyString(), anyString(), anyMap())).thenReturn(true);

            // 设备实时数据（包含最终充电量）
            Map<Object, Object> deviceData = new HashMap<>();
            deviceData.put("energy", "15.5");
            when(hashOperations.entries(anyString())).thenReturn(deviceData);

            // 计费明细
            PricingService.FeeDetail feeDetail = new PricingService.FeeDetail(
                    new BigDecimal("10.00"),
                    new BigDecimal("5.00"),
                    new BigDecimal("15.00")
            );
            when(pricingService.calculateExactFee(anyLong(), any(), any(), any())).thenReturn(feeDetail);

            when(chargeOrderMapper.updateById(any(ChargeOrder.class))).thenReturn(1);
            when(chargerMapper.updateById(any(Charger.class))).thenReturn(1);
        }
    }

    // ==================== getChargeStatus 实时状态查询 ====================

    @Nested
    @DisplayName("getChargeStatus 实时状态查询测试")
    class GetChargeStatusTest {

        @Test
        @DisplayName("订单不存在 → 抛出 BusinessException(404)")
        void getChargeStatus_orderNotFound() {
            when(chargeOrderMapper.selectOne(any())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> chargeService.getChargeStatus(ORDER_NO));

            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("正常查询 → 返回 ChargeStatusVO 含实时数据")
        void getChargeStatus_success() {
            when(chargeOrderMapper.selectOne(any())).thenReturn(chargingOrder);
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);

            // 模拟 Redis 中的设备实时数据
            Map<Object, Object> deviceData = new HashMap<>();
            deviceData.put("voltage", "220.0");
            deviceData.put("current", "32.0");
            deviceData.put("power", "7040.0");
            deviceData.put("energy", "15.5");
            deviceData.put("temperature", "42.5");
            when(hashOperations.entries(anyString())).thenReturn(deviceData);

            when(pricingService.estimateFee(anyLong(), any())).thenReturn(new BigDecimal("15.00"));

            ChargeStatusVO vo = chargeService.getChargeStatus(ORDER_NO);

            assertNotNull(vo);
            assertEquals(ORDER_NO, vo.getOrderNo());
            assertEquals(OrderStatusEnum.CHARGING.getCode(), vo.getOrderStatus());
            assertEquals(new BigDecimal("220.0"), vo.getVoltage());
            assertEquals(new BigDecimal("32.0"), vo.getCurrent());
            assertEquals(new BigDecimal("7040.0"), vo.getCurrentPower());
            assertEquals(new BigDecimal("15.5"), vo.getChargedEnergy());
            assertEquals(new BigDecimal("42.5"), vo.getTemperature());
            assertEquals(new BigDecimal("15.00"), vo.getEstimatedAmount());
            assertNotNull(vo.getStartTime());
            assertTrue(vo.getDurationSeconds() > 0);
        }
    }
}
