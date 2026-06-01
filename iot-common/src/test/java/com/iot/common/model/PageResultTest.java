package com.iot.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageResult 分页响应模型单元测试
 *
 * @author IoT Team
 */
@DisplayName("PageResult 分页响应模型")
class PageResultTest {

    @Nested
    @DisplayName("构造方法")
    class Constructor {

        @Test
        @DisplayName("全参构造应正确设置所有字段")
        void shouldSetAllFields() {
            List<String> records = Arrays.asList("a", "b", "c");
            PageResult<String> result = new PageResult<>(records, 30L, 2, 10);

            assertEquals(records, result.getRecords());
            assertEquals(30L, result.getTotal());
            assertEquals(2, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(3, result.getPages()); // 30/10=3
        }

        @Test
        @DisplayName("空记录列表应正确创建")
        void shouldHandleEmptyRecords() {
            PageResult<String> result = new PageResult<>(
                    Collections.emptyList(), 0L, 1, 10);

            assertTrue(result.getRecords().isEmpty());
            assertEquals(0L, result.getTotal());
            assertEquals(0, result.getPages());
        }
    }

    @Nested
    @DisplayName("pages 计算")
    class PagesCalculation {

        @Test
        @DisplayName("刚好整除时 pages=total/size")
        void shouldCalculateExactDivision() {
            PageResult<String> result = new PageResult<>(null, 100L, 1, 20);
            assertEquals(5, result.getPages());
        }

        @Test
        @DisplayName("有余数时 pages=total/size+1")
        void shouldRoundUp() {
            PageResult<String> result = new PageResult<>(null, 101L, 1, 20);
            assertEquals(6, result.getPages()); // 101/20=5 余1 → 6页
        }

        @Test
        @DisplayName("total=0 时 pages=0")
        void shouldReturnZeroWhenTotalIsZero() {
            PageResult<String> result = new PageResult<>(null, 0L, 1, 20);
            assertEquals(0, result.getPages());
        }

        @Test
        @DisplayName("total=1, size=20 时 pages=1")
        void shouldReturnOneForSingleItem() {
            PageResult<String> result = new PageResult<>(null, 1L, 1, 20);
            assertEquals(1, result.getPages());
        }
    }

    @Nested
    @DisplayName("无参构造 + Setter")
    class NoArgsConstructorAndSetters {

        @Test
        @DisplayName("通过 setter 设置后 getter 应正确返回")
        void shouldWorkWithSetters() {
            PageResult<Integer> result = new PageResult<>();
            result.setPage(1);
            result.setSize(15);
            result.setTotal(45L);
            result.setRecords(Arrays.asList(1, 2, 3));

            assertEquals(1, result.getPage());
            assertEquals(15, result.getSize());
            assertEquals(45L, result.getTotal());
            assertEquals(3, result.getPages()); // 45/15=3
            assertEquals(3, result.getRecords().size());
        }
    }
}
