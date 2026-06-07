package com.iot.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 所有枚举类的单元测试
 *
 * @author IoT Team
 */
@DisplayName("枚举类")
class EnumsTest {

    // ==================== DeviceStatusEnum ====================

    @Nested
    @DisplayName("DeviceStatusEnum 设备状态枚举")
    class DeviceStatusEnumTest {

        @ParameterizedTest
        @CsvSource({
                "0, OFFLINE, 离线",
                "1, IDLE, 空闲",
                "2, CHARGING, 充电中",
                "3, FAULT, 故障",
                "4, LOCKED, 锁定"
        })
        @DisplayName("fromCode 应根据状态码返回正确的枚举")
        void shouldReturnCorrectEnum(int code, String expectedName, String expectedDesc) {
            DeviceStatusEnum status = DeviceStatusEnum.fromCode(code);
            assertEquals(expectedName, status.name());
            assertEquals(code, status.getCode());
            assertEquals(expectedDesc, status.getDesc());
        }

        @Test
        @DisplayName("无效状态码应抛出 IllegalArgumentException")
        void shouldThrowForInvalidCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    DeviceStatusEnum.fromCode(99));
        }

        @Test
        @DisplayName("fromCode(-1) 应抛出异常")
        void shouldThrowForNegativeCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    DeviceStatusEnum.fromCode(-1));
        }

        @Test
        @DisplayName("values() 应包含 5 个状态")
        void shouldHaveFiveValues() {
            assertEquals(5, DeviceStatusEnum.values().length);
        }
    }

    // ==================== OrderStatusEnum ====================

    @Nested
    @DisplayName("OrderStatusEnum 订单状态枚举")
    class OrderStatusEnumTest {

        @ParameterizedTest
        @CsvSource({
                "0, PAY_PENDING, 待支付",
                "1, CHARGING, 充电中",
                "2, COMPLETED, 已完成",
                "3, CANCELLED, 已取消",
                "4, ABNORMAL, 异常",
                "5, PENDING_CONFIRM, 待支付",
                "6, AWAITING_DEVICE, 等待设备"
        })
        @DisplayName("fromCode 应根据状态码返回正确的枚举")
        void shouldReturnCorrectEnum(int code, String expectedName, String expectedDesc) {
            OrderStatusEnum status = OrderStatusEnum.fromCode(code);
            assertEquals(expectedName, status.name());
            assertEquals(expectedDesc, status.getDesc());
        }

        @Test
        @DisplayName("无效状态码应抛出异常")
        void shouldThrowForInvalidCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    OrderStatusEnum.fromCode(99));
        }

        @Test
        @DisplayName("values() 应包含 7 个状态")
        void shouldHaveSevenValues() {
            assertEquals(7, OrderStatusEnum.values().length);
        }
    }

    // ==================== PayStatusEnum ====================

    @Nested
    @DisplayName("PayStatusEnum 支付状态枚举")
    class PayStatusEnumTest {

        @ParameterizedTest
        @CsvSource({
                "0, UNPAID, 未支付",
                "1, PAID, 已支付",
                "2, REFUNDED, 已退款"
        })
        @DisplayName("fromCode 应根据状态码返回正确的枚举")
        void shouldReturnCorrectEnum(int code, String expectedName, String expectedDesc) {
            PayStatusEnum status = PayStatusEnum.fromCode(code);
            assertEquals(expectedName, status.name());
            assertEquals(expectedDesc, status.getDesc());
        }

        @Test
        @DisplayName("fromCode(99) 应抛出异常")
        void shouldThrowForInvalidCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    PayStatusEnum.fromCode(99));
        }

        @Test
        @DisplayName("values() 应包含 3 个状态")
        void shouldHaveThreeValues() {
            assertEquals(3, PayStatusEnum.values().length);
        }
    }

    // ==================== AlarmLevelEnum ====================

    @Nested
    @DisplayName("AlarmLevelEnum 告警级别枚举")
    class AlarmLevelEnumTest {

        @ParameterizedTest
        @CsvSource({
                "1, GENERAL, 一般",
                "2, IMPORTANT, 重要",
                "3, URGENT, 紧急"
        })
        @DisplayName("fromCode 应根据级别码返回正确的枚举")
        void shouldReturnCorrectEnum(int code, String expectedName, String expectedDesc) {
            AlarmLevelEnum level = AlarmLevelEnum.fromCode(code);
            assertEquals(expectedName, level.name());
            assertEquals(expectedDesc, level.getDesc());
        }

        @Test
        @DisplayName("fromCode(0) 应抛出异常（不存在级别0）")
        void shouldThrowForCodeZero() {
            assertThrows(IllegalArgumentException.class, () ->
                    AlarmLevelEnum.fromCode(0));
        }

        @Test
        @DisplayName("fromCode(99) 应抛出异常")
        void shouldThrowForInvalidCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    AlarmLevelEnum.fromCode(99));
        }

        @Test
        @DisplayName("values() 应包含 3 个级别")
        void shouldHaveThreeValues() {
            assertEquals(3, AlarmLevelEnum.values().length);
        }
    }
}
