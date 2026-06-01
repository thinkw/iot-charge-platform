package com.iot.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花算法ID生成工具类
 * <p>
 * 基于 Hutool 的 Snowflake 实现，用于生成分布式全局唯一ID（long类型）。
 * 适用于数据库主键、订单号等需要唯一标识的场景。
 * 默认使用当前应用实例的 workerId=1 和 datacenterId=1。
 * 在多实例部署时，需为每个实例分配不同的 workerId 和 datacenterId。
 * </p>
 *
 * @author IoT Team
 */
public class SnowflakeIdUtil {

    /** Snowflake 实例 */
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    /**
     * 私有构造方法，防止实例化
     */
    private SnowflakeIdUtil() {
    }

    /**
     * 生成下一个唯一ID
     *
     * @return 全局唯一 long 类型 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成下一个唯一ID的字符串形式
     *
     * @return 全局唯一 ID 的字符串形式
     */
    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    /**
     * 解析雪花ID，获取其组成部分（时间戳、datacenterId、workerId、序列号）
     * <p>
     * Hutool Snowflake 默认使用 2020-01-01 为起始时间戳，
     * ID 结构：41位时间戳 | 5位datacenterId | 5位workerId | 12位序列号
     * </p>
     *
     * @param id 雪花ID
     * @return 解析后的 ID 组成部分字符串
     */
    public static String parseId(long id) {
        // Hutool Snowflake 默认起始时间：2020-01-01 00:00:00
        long epoch = 1577836800000L;
        long timestamp = (id >> 22) + epoch;
        long datacenterId = (id >> 17) & 0x1F;
        long workerId = (id >> 12) & 0x1F;
        long sequence = id & 0xFFF;

        return String.format("SnowflakeId{timestamp=%s, datacenterId=%d, workerId=%d, sequence=%d}",
                DateTimeUtil.format(java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(timestamp),
                        java.time.ZoneId.systemDefault())),
                datacenterId, workerId, sequence);
    }
}
