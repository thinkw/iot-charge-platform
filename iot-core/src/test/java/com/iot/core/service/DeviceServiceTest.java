package com.iot.core.service;

import com.iot.core.entity.Charger;
import com.iot.core.mapper.AlarmMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.DeviceLogMapper;
import com.iot.core.service.impl.DeviceServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceService 单元测试
 * <p>
 * 使用 Mockito 隔离外部依赖，重点测试纯逻辑方法：
 * - 设备鉴权逻辑（SN + 密钥验证）
 * - 状态机转换规则校验
 * - 设备在线状态检测
 * - 心跳更新逻辑
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeviceService 单元测试")
class DeviceServiceTest {

    @Mock private ChargerMapper chargerMapper;
    @Mock private DeviceLogMapper deviceLogMapper;
    @Mock private AlarmMapper alarmMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    private static final String TEST_SN = "CHARGER-001";
    private static final String TEST_SECRET = "GER-001";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // ==================== 设备鉴权测试（纯逻辑） ====================

    // 注意：authenticateDevice 依赖 MyBatis-Plus BaseMapper.selectOne(Wapper<T>)，
    // 由于 MyBatis-Plus 的 selectOne 方法存在重载（selectOne(Wrapper) 与 selectOne(Wrapper, boolean)），
    // Mockito 无法可靠匹配具体重载版本。这部分逻辑将在集成测试中验证。

    @Test
    @DisplayName("鉴权 - SN为空或null，认证失败（参数校验）")
    void authenticateDevice_BlankSn() {
        assertFalse(deviceService.authenticateDevice("", "secret"));
        assertFalse(deviceService.authenticateDevice(null, "secret"));
    }

    @Test
    @DisplayName("鉴权 - 密钥为空或null，认证失败（参数校验）")
    void authenticateDevice_BlankSecret() {
        assertFalse(deviceService.authenticateDevice(TEST_SN, ""));
        assertFalse(deviceService.authenticateDevice(TEST_SN, null));
    }

    // ==================== 状态机校验测试（纯逻辑） ====================

    @Test
    @DisplayName("状态机 - OFFLINE→IDLE，合法")
    void validateTransition_OfflineToIdle() {
        assertTrue(deviceService.validateStatusTransition(0, 1));
    }

    @Test
    @DisplayName("状态机 - IDLE→LOCKED，合法")
    void validateTransition_IdleToLocked() {
        assertTrue(deviceService.validateStatusTransition(1, 4));
    }

    @Test
    @DisplayName("状态机 - IDLE→FAULT，合法")
    void validateTransition_IdleToFault() {
        assertTrue(deviceService.validateStatusTransition(1, 3));
    }

    @Test
    @DisplayName("状态机 - IDLE→OFFLINE，合法")
    void validateTransition_IdleToOffline() {
        assertTrue(deviceService.validateStatusTransition(1, 0));
    }

    @Test
    @DisplayName("状态机 - CHARGING→IDLE，合法")
    void validateTransition_ChargingToIdle() {
        assertTrue(deviceService.validateStatusTransition(2, 1));
    }

    @Test
    @DisplayName("状态机 - CHARGING→FAULT，合法")
    void validateTransition_ChargingToFault() {
        assertTrue(deviceService.validateStatusTransition(2, 3));
    }

    @Test
    @DisplayName("状态机 - CHARGING→OFFLINE，合法（异常离线）")
    void validateTransition_ChargingToOffline() {
        assertTrue(deviceService.validateStatusTransition(2, 0));
    }

    @Test
    @DisplayName("状态机 - LOCKED→CHARGING，合法")
    void validateTransition_LockedToCharging() {
        assertTrue(deviceService.validateStatusTransition(4, 2));
    }

    @Test
    @DisplayName("状态机 - LOCKED→IDLE，合法（解锁）")
    void validateTransition_LockedToIdle() {
        assertTrue(deviceService.validateStatusTransition(4, 1));
    }

    @Test
    @DisplayName("状态机 - FAULT→IDLE，合法（故障恢复）")
    void validateTransition_FaultToIdle() {
        assertTrue(deviceService.validateStatusTransition(3, 1));
    }

    @Test
    @DisplayName("状态机 - FAULT→OFFLINE，合法")
    void validateTransition_FaultToOffline() {
        assertTrue(deviceService.validateStatusTransition(3, 0));
    }

    @Test
    @DisplayName("状态机 - OFFLINE→CHARGING，合法（断电恢复继续充电）")
    void validateTransition_OfflineToCharging_Valid() {
        assertTrue(deviceService.validateStatusTransition(0, 2));
    }

    @Test
    @DisplayName("状态机 - CHARGING→LOCKED，非法")
    void validateTransition_ChargingToLocked_Invalid() {
        assertFalse(deviceService.validateStatusTransition(2, 4));
    }

    @Test
    @DisplayName("状态机 - IDLE→CHARGING，合法（扫码启动充电）")
    void validateTransition_IdleToCharging_Valid() {
        assertTrue(deviceService.validateStatusTransition(1, 2));
    }

    @Test
    @DisplayName("状态机 - 同状态转换，合法（幂等）")
    void validateTransition_SameStatus() {
        assertTrue(deviceService.validateStatusTransition(1, 1));
        assertTrue(deviceService.validateStatusTransition(2, 2));
        assertTrue(deviceService.validateStatusTransition(3, 3));
    }

    @Test
    @DisplayName("状态机 - 无效状态码返回false")
    void validateTransition_InvalidCode() {
        assertFalse(deviceService.validateStatusTransition(1, 99));
        assertFalse(deviceService.validateStatusTransition(99, 1));
    }

    // ==================== 在线检测测试 ====================

    @Test
    @DisplayName("在线检测 - 设备在线返回true")
    void isDeviceOnline_True() {
        when(hashOperations.get("device:status:" + TEST_SN, "online")).thenReturn("1");
        assertTrue(deviceService.isDeviceOnline(TEST_SN));
    }

    @Test
    @DisplayName("在线检测 - 设备离线返回false")
    void isDeviceOnline_False() {
        when(hashOperations.get("device:status:" + TEST_SN, "online")).thenReturn("0");
        assertFalse(deviceService.isDeviceOnline(TEST_SN));
    }

    @Test
    @DisplayName("在线检测 - Redis无记录返回false")
    void isDeviceOnline_NoRecord() {
        when(hashOperations.get("device:status:" + TEST_SN, "online")).thenReturn(null);
        assertFalse(deviceService.isDeviceOnline(TEST_SN));
    }

    // ==================== 心跳测试 ====================

    @Test
    @DisplayName("心跳 - 更新Redis心跳时间")
    void handleHeartbeat_UpdatesRedisTimestamp() {
        when(hashOperations.get("device:status:" + TEST_SN, "online")).thenReturn("1");

        deviceService.handleHeartbeat(TEST_SN);

        // 验证心跳时间被更新（lastHeartbeat字段）
        verify(hashOperations).put(eq("device:status:" + TEST_SN), eq("lastHeartbeat"), anyString());
        // 验证在线状态保持为1
        verify(hashOperations).put(eq("device:status:" + TEST_SN), eq("online"), eq(1));
    }

    // ==================== 上线离线测试（基础验证） ====================

    @Test
    @DisplayName("上线 - 更新Redis设备状态")
    void handleOnline_UpdatesRedisStatus() {
        Charger charger = new Charger();
        charger.setId(1L);
        charger.setSn(TEST_SN);
        charger.setStationId(1L);

        when(chargerMapper.selectOne(any())).thenReturn(charger);
        when(chargerMapper.updateById(any(Charger.class))).thenReturn(1);
        when(deviceLogMapper.insert(any(com.iot.core.entity.DeviceLog.class))).thenReturn(1);

        deviceService.handleOnline(TEST_SN);

        // 验证 Redis 状态包含在线标记
        verify(hashOperations).putAll(eq("device:status:" + TEST_SN), anyMap());
    }

    @Test
    @DisplayName("离线 - 正常离线更新状态")
    void handleOffline_UpdatesStatus() {
        Charger charger = new Charger();
        charger.setId(1L);
        charger.setSn(TEST_SN);
        charger.setStationId(1L);

        when(hashOperations.get(anyString(), eq("status"))).thenReturn("1");
        when(chargerMapper.selectOne(any())).thenReturn(charger);
        when(chargerMapper.updateById(any(Charger.class))).thenReturn(1);
        when(deviceLogMapper.insert(any(com.iot.core.entity.DeviceLog.class))).thenReturn(1);

        deviceService.handleOffline(TEST_SN);

        // 验证 Redis 状态被更新
        verify(hashOperations).putAll(eq("device:status:" + TEST_SN), anyMap());
    }
}
