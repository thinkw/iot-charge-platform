package com.iot.core.service;

import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.core.dto.request.CreateReservationRequest;
import com.iot.core.entity.Charger;
import com.iot.core.entity.ReservationOrder;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.ReservationOrderMapper;
import com.iot.core.service.impl.ReservationServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReservationServiceImpl 单元测试
 * <p>
 * 覆盖预约创建与取消两个核心流程，
 * 使用 Mockito 隔离 ReservationOrderMapper / ChargerMapper / RedissonClient 等外部依赖。
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReservationService 单元测试")
class ReservationServiceTest {

    @Mock private ReservationOrderMapper reservationOrderMapper;
    @Mock private ChargerMapper chargerMapper;
    @Mock private RedissonClient redissonClient;

    @Mock private RLock lock;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private static final Long USER_ID = 1L;
    private static final Long ANOTHER_USER_ID = 2L;
    private static final Long CHARGER_ID = 1L;
    private static final Long STATION_ID = 1L;
    private static final String ORDER_NO = "RES202501010001";
    private static final LocalDate RESERVE_DATE = LocalDate.now().plusDays(1);

    private Charger idleCharger;
    private Charger chargingCharger;
    private ReservationOrder pendingOrder;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 通用 Mock 配置
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        // 空闲充电桩
        idleCharger = new Charger();
        idleCharger.setId(CHARGER_ID);
        idleCharger.setSn("CHARGER-001");
        idleCharger.setStationId(STATION_ID);
        idleCharger.setStatus(DeviceStatusEnum.IDLE.getCode());

        // 充电中充电桩
        chargingCharger = new Charger();
        chargingCharger.setId(CHARGER_ID);
        chargingCharger.setSn("CHARGER-001");
        chargingCharger.setStationId(STATION_ID);
        chargingCharger.setStatus(DeviceStatusEnum.CHARGING.getCode());

        // 待使用预约订单
        pendingOrder = new ReservationOrder();
        pendingOrder.setOrderNo(ORDER_NO);
        pendingOrder.setUserId(USER_ID);
        pendingOrder.setChargerId(CHARGER_ID);
        pendingOrder.setStationId(STATION_ID);
        pendingOrder.setReserveDate(RESERVE_DATE);
        pendingOrder.setStartTime(LocalTime.of(10, 0));
        pendingOrder.setEndTime(LocalTime.of(12, 0));
        pendingOrder.setDeposit(new BigDecimal("30.00"));
        pendingOrder.setPenalty(new BigDecimal("10.00"));
        pendingOrder.setStatus(0); // STATUS_PENDING
        pendingOrder.setPayStatus(0);
    }

    // ==================== createReservation 创建预约 ====================

    @Nested
    @DisplayName("createReservation 创建预约测试")
    class CreateReservationTest {

        @Test
        @DisplayName("充电桩不存在 → BusinessException(404)")
        void createReservation_chargerNotFound() {
            CreateReservationRequest request = createValidRequest();
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(USER_ID, request));

            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("充电桩状态为充电中 → BusinessException(409)")
        void createReservation_chargerCharging() {
            CreateReservationRequest request = createValidRequest();
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(chargingCharger);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(USER_ID, request));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("预约结束时间≤开始时间 → BusinessException(400)")
        void createReservation_invalidTimeRange() {
            CreateReservationRequest request = createValidRequest();
            // 结束时间早于开始时间 → 非法
            request.setEndTime(LocalTime.of(8, 0));
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(USER_ID, request));

            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("时段已被占用（conflictCount>0）→ BusinessException(409)")
        void createReservation_timeConflict() {
            CreateReservationRequest request = createValidRequest();
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(reservationOrderMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(USER_ID, request));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("分布式锁获取失败 → BusinessException(429)")
        void createReservation_lockFailed() throws InterruptedException {
            CreateReservationRequest request = createValidRequest();
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(reservationOrderMapper.selectCount(any())).thenReturn(0L);
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(USER_ID, request));

            assertEquals(429, ex.getCode());
        }

        @Test
        @DisplayName("正常创建 → 返回预约编号(RES开头)")
        void createReservation_success() {
            CreateReservationRequest request = createValidRequest();
            when(chargerMapper.selectById(CHARGER_ID)).thenReturn(idleCharger);
            when(reservationOrderMapper.selectCount(any())).thenReturn(0L);
            when(reservationOrderMapper.insert(any(ReservationOrder.class))).thenReturn(1);

            String orderNo = reservationService.createReservation(USER_ID, request);

            assertNotNull(orderNo);
            assertTrue(orderNo.startsWith("RES"));
        }

        /**
         * 创建有效预约请求的辅助方法
         */
        private CreateReservationRequest createValidRequest() {
            CreateReservationRequest request = new CreateReservationRequest();
            request.setChargerId(CHARGER_ID);
            request.setReserveDate(RESERVE_DATE);
            request.setStartTime(LocalTime.of(10, 0));
            request.setEndTime(LocalTime.of(12, 0));
            return request;
        }
    }

    // ==================== cancelReservation 取消预约 ====================

    @Nested
    @DisplayName("cancelReservation 取消预约测试")
    class CancelReservationTest {

        @Test
        @DisplayName("预约不存在 → BusinessException(404)")
        void cancelReservation_notFound() {
            when(reservationOrderMapper.selectOne(any())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.cancelReservation(ORDER_NO, USER_ID));

            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("预约不属于当前用户 → BusinessException(403)")
        void cancelReservation_notBelongToUser() {
            when(reservationOrderMapper.selectOne(any())).thenReturn(pendingOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.cancelReservation(ORDER_NO, ANOTHER_USER_ID));

            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("预约状态不是待使用 → BusinessException(409)")
        void cancelReservation_notPending() {
            // 将预约状态设为"已使用"(1)，而非"待使用"(0)
            pendingOrder.setStatus(1);
            when(reservationOrderMapper.selectOne(any())).thenReturn(pendingOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.cancelReservation(ORDER_NO, USER_ID));

            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("正常取消 → status=已取消(2)")
        void cancelReservation_success() {
            when(reservationOrderMapper.selectOne(any())).thenReturn(pendingOrder);
            when(reservationOrderMapper.updateById(any(ReservationOrder.class))).thenReturn(1);

            assertDoesNotThrow(() -> reservationService.cancelReservation(ORDER_NO, USER_ID));

            // 验证 updateById 传入的预约状态已更新为"已取消"(2)
            verify(reservationOrderMapper).updateById(ArgumentMatchers.<ReservationOrder>argThat(order ->
                    order.getStatus() == 2
            ));
        }
    }
}
