package com.iot.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseEntity 基础实体单元测试
 *
 * @author IoT Team
 */
@DisplayName("BaseEntity 基础实体")
class BaseEntityTest {

    /**
     * 具体实现类，用于测试抽象 BaseEntity
     */
    static class TestEntity extends BaseEntity {
    }

    @Test
    @DisplayName("子类应能正常实例化")
    void shouldInstantiateSubclass() {
        TestEntity entity = new TestEntity();
        assertNotNull(entity);
    }

    @Test
    @DisplayName("id 字段应能正常存取")
    void shouldSetAndGetId() {
        TestEntity entity = new TestEntity();
        entity.setId(123456789L);
        assertEquals(123456789L, entity.getId());
    }

    @Test
    @DisplayName("新实例 id 应为 null")
    void shouldHaveNullIdByDefault() {
        TestEntity entity = new TestEntity();
        assertNull(entity.getId());
    }

    @Test
    @DisplayName("createTime 应能正常存取")
    void shouldSetAndGetCreateTime() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        assertEquals(now, entity.getCreateTime());
    }

    @Test
    @DisplayName("updateTime 应能正常存取")
    void shouldSetAndGetUpdateTime() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdateTime(now);
        assertEquals(now, entity.getUpdateTime());
    }
}
