package com.iot.core.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置类
 * <p>
 * 从 application.yml 中读取 Redis 连接配置（host、port、password），
 * 创建 RedissonClient 单例 Bean，用于实现分布式锁和分布式集合操作。
 * </p>
 *
 * @author IoT Team
 */
@Configuration
public class RedissonConfig {

    /**
     * Redis 主机地址，默认 localhost
     */
    @Value("${spring.data.redis.host:localhost}")
    private String host;

    /**
     * Redis 端口号，默认 6379
     */
    @Value("${spring.data.redis.port:6379}")
    private int port;

    /**
     * Redis 密码，默认为空（无需密码）
     */
    @Value("${spring.data.redis.password:}")
    private String password;

    /**
     * 创建 RedissonClient Bean
     * <p>
     * 使用单节点模式连接 Redis，地址格式为 redis://host:port。
     * 如果配置了密码，则设置密码认证。
     * </p>
     *
     * @return RedissonClient 实例
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 构建单节点 Redis 连接地址
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setPassword(password.isEmpty() ? null : password);
        return Redisson.create(config);
    }
}
