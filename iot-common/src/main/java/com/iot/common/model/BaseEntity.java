package com.iot.common.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类
 * <p>
 * 所有数据库实体的公共基类，包含通用字段：
 * - id：主键（使用雪花算法生成）
 * - createTime：创建时间
 * - updateTime：更新时间
 * <p>
 * 注意：本模块为纯 Java 库，不依赖 MyBatis-Plus 注解。
 * 使用方（如 iot-core）可通过继承并添加 @TableName、@TableId 等注解来适配 ORM 框架。
 * </p>
 *
 * @author IoT Team
 */
@Data
public abstract class BaseEntity {

    /**
     * 主键ID
     * 建议使用雪花算法生成，由 SnowflakeIdUtil 提供
     */
    private Long id;

    /**
     * 创建时间
     * 插入时自动填充，由数据库或应用层设置
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 更新时自动填充，由数据库或应用层设置
     */
    private LocalDateTime updateTime;
}
