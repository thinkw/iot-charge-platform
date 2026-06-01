package com.iot.core.config;

import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置类
 * <p>
 * 本模块的 RocketMQ 生产者、消费者定义在 mq 包下按需创建。
 * 该配置类仅为模块预留，具体的 Producer/Consumer 配置详见：
 * - {@code com.iot.core.mq.producer} 生产者包
 * - {@code com.iot.core.mq.consumer} 消费者包
 * </p>
 *
 * @author IoT Team
 */
// RocketMQ 生产者/消费者在 mq 包下按需创建
@Configuration
public class RocketMQConfig {
    // RocketMQ 相关配置在 mq 子包中实现，此处仅作为配置入口标记
}
