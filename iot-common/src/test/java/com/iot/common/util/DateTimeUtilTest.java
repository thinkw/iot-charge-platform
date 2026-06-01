package com.iot.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateTimeUtil 日期时间工具类单元测试
 *
 * @author IoT Team
 */
@DisplayName("DateTimeUtil 日期时间工具")
class DateTimeUtilTest {

    @Nested
    @DisplayName("now()")
    class Now {

        @Test
        @DisplayName("应返回非空值")
        void shouldReturnNonNull() {
            LocalDateTime now = DateTimeUtil.now();
            assertNotNull(now);
        }

        @Test
        @DisplayName("应返回当前时间（误差不超过1秒）")
        void shouldReturnCurrentTimeWithinTolerance() {
            LocalDateTime now = DateTimeUtil.now();
            long diff = Math.abs(ChronoUnit.SECONDS.between(LocalDateTime.now(), now));
            assertTrue(diff <= 1, "时间差应小于1秒，实际差: " + diff + "秒");
        }
    }

    @Nested
    @DisplayName("format(LocalDateTime, String)")
    class FormatWithPattern {

        @Test
        @DisplayName("应正确格式化为指定格式")
        void shouldFormatCorrectly() {
            LocalDateTime time = LocalDateTime.of(2026, 6, 1, 12, 0, 0);
            String result = DateTimeUtil.format(time, "yyyy-MM-dd HH:mm:ss");
            assertEquals("2026-06-01 12:00:00", result);
        }

        @Test
        @DisplayName("仅日期格式")
        void shouldFormatDateOnly() {
            LocalDateTime time = LocalDateTime.of(2026, 6, 1, 0, 0);
            String result = DateTimeUtil.format(time, "yyyy-MM-dd");
            assertEquals("2026-06-01", result);
        }
    }

    @Nested
    @DisplayName("format(LocalDateTime)")
    class FormatDefault {

        @Test
        @DisplayName("应使用默认格式 yyyy-MM-dd HH:mm:ss")
        void shouldUseDefaultPattern() {
            LocalDateTime time = LocalDateTime.of(2026, 1, 15, 8, 30, 45);
            String result = DateTimeUtil.format(time);
            assertEquals("2026-01-15 08:30:45", result);
        }
    }

    @Nested
    @DisplayName("parse(String, String)")
    class ParseWithPattern {

        @Test
        @DisplayName("应正确解析指定格式的日期字符串")
        void shouldParseCorrectly() {
            LocalDateTime result = DateTimeUtil.parse("2026-06-01 12:00:00", "yyyy-MM-dd HH:mm:ss");
            assertEquals(LocalDateTime.of(2026, 6, 1, 12, 0, 0), result);
        }

        @Test
        @DisplayName("不匹配格式的字符串应抛出异常")
        void shouldThrowForMismatchedFormat() {
            assertThrows(DateTimeParseException.class, () ->
                    DateTimeUtil.parse("2026/06/01", "yyyy-MM-dd HH:mm:ss"));
        }

        @Test
        @DisplayName("空字符串应返回 null（防御性设计）")
        void shouldReturnNullForEmptyString() {
            LocalDateTime result = DateTimeUtil.parse("", "yyyy-MM-dd HH:mm:ss");
            assertNull(result, "空字符串应返回 null");
        }

        @Test
        @DisplayName("null 字符串应返回 null")
        void shouldReturnNullForNullString() {
            LocalDateTime result = DateTimeUtil.parse(null, "yyyy-MM-dd HH:mm:ss");
            assertNull(result, "null 输入应返回 null");
        }
    }

    @Nested
    @DisplayName("parse(String)")
    class ParseDefault {

        @Test
        @DisplayName("应使用默认格式解析")
        void shouldUseDefaultPattern() {
            LocalDateTime result = DateTimeUtil.parse("2026-06-01 12:00:00");
            assertEquals(LocalDateTime.of(2026, 6, 1, 12, 0, 0), result);
        }
    }

    @Nested
    @DisplayName("format → parse 往返")
    class RoundTrip {

        @Test
        @DisplayName("格式化后再解析应得到原始时间")
        void shouldRoundTripCorrectly() {
            LocalDateTime original = LocalDateTime.of(2026, 6, 1, 14, 30, 0);
            String formatted = DateTimeUtil.format(original);
            LocalDateTime parsed = DateTimeUtil.parse(formatted);
            assertEquals(original, parsed);
        }
    }
}
