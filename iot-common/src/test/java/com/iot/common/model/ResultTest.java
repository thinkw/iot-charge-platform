package com.iot.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应模型单元测试
 *
 * @author IoT Team
 */
@DisplayName("Result 统一响应模型")
class ResultTest {

    @Nested
    @DisplayName("success() 无数据")
    class SuccessWithoutData {

        @Test
        @DisplayName("应返回 code=200")
        void shouldReturnCode200() {
            Result<Void> result = Result.success();
            assertEquals(200, result.getCode());
        }

        @Test
        @DisplayName("应返回默认成功消息")
        void shouldReturnSuccessMessage() {
            Result<Void> result = Result.success();
            assertEquals("操作成功", result.getMessage());
        }

        @Test
        @DisplayName("data 应为 null")
        void shouldReturnNullData() {
            Result<Void> result = Result.success();
            assertNull(result.getData());
        }

        @Test
        @DisplayName("timestamp 不应为空")
        void shouldHaveTimestamp() {
            Result<Void> result = Result.success();
            assertNotNull(result.getTimestamp());
            assertTrue(result.getTimestamp() > 0);
        }

        @Test
        @DisplayName("isSuccess() 应返回 true")
        void shouldReturnTrueForIsSuccess() {
            Result<Void> result = Result.success();
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("success(T data) 带数据")
    class SuccessWithData {

        @Test
        @DisplayName("应返回传入的数据")
        void shouldReturnGivenData() {
            String data = "test-data";
            Result<String> result = Result.success(data);
            assertEquals("test-data", result.getData());
        }

        @Test
        @DisplayName("应返回 code=200")
        void shouldReturnCode200() {
            Result<Integer> result = Result.success(42);
            assertEquals(200, result.getCode());
        }
    }

    @Nested
    @DisplayName("error(int code, String message)")
    class ErrorWithCode {

        @Test
        @DisplayName("应返回指定的错误码和消息")
        void shouldReturnSpecifiedCodeAndMessage() {
            Result<Void> result = Result.error(404, "资源不存在");
            assertEquals(404, result.getCode());
            assertEquals("资源不存在", result.getMessage());
        }

        @Test
        @DisplayName("data 应为 null")
        void shouldReturnNullData() {
            Result<Void> result = Result.error(400, "参数错误");
            assertNull(result.getData());
        }

        @Test
        @DisplayName("isSuccess() 应返回 false")
        void shouldReturnFalseForIsSuccess() {
            Result<Void> result = Result.error(500, "服务器错误");
            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("error(String message) 默认500")
    class ErrorDefault {

        @Test
        @DisplayName("应默认返回 code=500")
        void shouldDefaultToCode500() {
            Result<Void> result = Result.error("内部错误");
            assertEquals(500, result.getCode());
        }

        @Test
        @DisplayName("应返回指定的错误消息")
        void shouldReturnSpecifiedMessage() {
            Result<Void> result = Result.error("服务不可用");
            assertEquals("服务不可用", result.getMessage());
        }
    }

    @Nested
    @DisplayName("isSuccess() 边界情况")
    class IsSuccessEdgeCases {

        @Test
        @DisplayName("code 为 null 时应返回 false")
        void shouldReturnFalseWhenCodeIsNull() {
            Result<Void> result = new Result<>();
            result.setCode(null);
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("code 为 201 时应返回 false")
        void shouldReturnFalseForNon200() {
            Result<Void> result = Result.success();
            result.setCode(201);
            assertFalse(result.isSuccess());
        }
    }
}
