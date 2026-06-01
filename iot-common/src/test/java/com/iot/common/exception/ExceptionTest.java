package com.iot.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常类单元测试
 *
 * @author IoT Team
 */
@DisplayName("异常类")
class ExceptionTest {

    // ==================== BusinessException ====================

    @Nested
    @DisplayName("BusinessException 业务异常")
    class BusinessExceptionTest {

        @Test
        @DisplayName("(int code, String message) 构造应正确设置 code 和 message")
        void shouldSetCodeAndMessage() {
            BusinessException ex = new BusinessException(409, "充电桩已被占用");

            assertEquals(409, ex.getCode());
            assertEquals("充电桩已被占用", ex.getMessage());
        }

        @Test
        @DisplayName("(String message) 构造应默认 code=500")
        void shouldDefaultToCode500() {
            BusinessException ex = new BusinessException("内部错误");

            assertEquals(500, ex.getCode());
            assertEquals("内部错误", ex.getMessage());
        }

        @Test
        @DisplayName("应继承自 RuntimeException")
        void shouldExtendRuntimeException() {
            BusinessException ex = new BusinessException("test");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    // ==================== DeviceOfflineException ====================

    @Nested
    @DisplayName("DeviceOfflineException 设备离线异常")
    class DeviceOfflineExceptionTest {

        @Test
        @DisplayName("应包含设备标识和 code=409")
        void shouldContainDeviceId() {
            DeviceOfflineException ex = new DeviceOfflineException("CHARGER-001");

            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains("CHARGER-001"));
            assertTrue(ex.getMessage().contains("离线"));
        }

        @Test
        @DisplayName("应继承自 BusinessException")
        void shouldExtendBusinessException() {
            DeviceOfflineException ex = new DeviceOfflineException("CHARGER-001");
            assertTrue(ex instanceof BusinessException);
        }
    }

    // ==================== OrderException ====================

    @Nested
    @DisplayName("OrderException 订单异常")
    class OrderExceptionTest {

        @Test
        @DisplayName("new OrderException(code, message) 应正确设置 code 和 message")
        void shouldSetCodeAndMessage() {
            OrderException ex = new OrderException(409, "订单状态异常");

            assertEquals(409, ex.getCode());
            assertEquals("订单状态异常", ex.getMessage());
        }

        @Test
        @DisplayName("new OrderException(message) 应默认 code=409")
        void shouldDefaultToCode409() {
            OrderException ex = new OrderException("订单已取消");

            assertEquals(409, ex.getCode());
            assertEquals("订单已取消", ex.getMessage());
        }

        @Test
        @DisplayName("应继承自 BusinessException")
        void shouldExtendBusinessException() {
            OrderException ex = new OrderException("订单错误");
            assertTrue(ex instanceof BusinessException);
        }
    }
}
