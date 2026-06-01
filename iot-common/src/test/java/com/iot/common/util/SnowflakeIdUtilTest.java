package com.iot.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnowflakeIdUtil 雪花ID工具类单元测试
 *
 * @author IoT Team
 */
@DisplayName("SnowflakeIdUtil 雪花ID工具")
class SnowflakeIdUtilTest {

    @Nested
    @DisplayName("nextId()")
    class NextId {

        @Test
        @DisplayName("应生成大于 0 的 ID")
        void shouldGeneratePositiveId() {
            long id = SnowflakeIdUtil.nextId();
            assertTrue(id > 0, "雪花ID应为正数");
        }

        @Test
        @DisplayName("连续两次生成 ID 应不同且递增")
        void shouldGenerateUniqueAndIncreasingIds() {
            long id1 = SnowflakeIdUtil.nextId();
            long id2 = SnowflakeIdUtil.nextId();

            assertNotEquals(id1, id2, "两次生成的ID应不同");
            assertTrue(id2 > id1, "后生成的ID应大于先生成的ID");
        }

        @Test
        @DisplayName("快速生成 100 个 ID 应全部唯一")
        void shouldGenerate100UniqueIds() {
            // 使用 Long 哈希集验证唯一性
            long first = SnowflakeIdUtil.nextId();
            long last = first;
            for (int i = 1; i < 100; i++) {
                long id = SnowflakeIdUtil.nextId();
                assertTrue(id > last, "第" + i + "个ID(" + id + ")应大于前一个(" + last + ")");
                last = id;
            }
        }
    }

    @Nested
    @DisplayName("nextIdStr()")
    class NextIdStr {

        @Test
        @DisplayName("应返回纯数字字符串")
        void shouldReturnNumericString() {
            String idStr = SnowflakeIdUtil.nextIdStr();
            assertNotNull(idStr);
            assertFalse(idStr.isEmpty());
            assertTrue(idStr.matches("\\d+"), "ID字符串应只包含数字，实际值: " + idStr);
        }

        @Test
        @DisplayName("连续两次生成应不同")
        void shouldGenerateDifferentStrings() {
            String id1 = SnowflakeIdUtil.nextIdStr();
            String id2 = SnowflakeIdUtil.nextIdStr();

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("应能通过 Long.parseLong 解析")
        void shouldBeParseableAsLong() {
            String idStr = SnowflakeIdUtil.nextIdStr();
            long id = Long.parseLong(idStr);
            assertTrue(id > 0);
        }
    }

    @Nested
    @DisplayName("parseId(long)")
    class ParseId {

        @Test
        @DisplayName("应返回非空字符串")
        void shouldReturnNonNullString() {
            long id = SnowflakeIdUtil.nextId();
            String result = SnowflakeIdUtil.parseId(id);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("结果应包含 SnowflakeId 标识")
        void shouldContainSnowflakeIdPrefix() {
            long id = SnowflakeIdUtil.nextId();
            String result = SnowflakeIdUtil.parseId(id);
            assertTrue(result.contains("SnowflakeId{"), "实际结果: " + result);
        }

        @Test
        @DisplayName("结果应包含各组成部分的字段名")
        void shouldContainComponentNames() {
            long id = SnowflakeIdUtil.nextId();
            String result = SnowflakeIdUtil.parseId(id);
            assertTrue(result.contains("timestamp="));
            assertTrue(result.contains("datacenterId="));
            assertTrue(result.contains("workerId="));
            assertTrue(result.contains("sequence="));
        }

        @Test
        @DisplayName("对同个 ID 多次调用应返回相同结果")
        void shouldBeDeterministic() {
            long id = SnowflakeIdUtil.nextId();
            String result1 = SnowflakeIdUtil.parseId(id);
            String result2 = SnowflakeIdUtil.parseId(id);
            assertEquals(result1, result2);
        }
    }
}
