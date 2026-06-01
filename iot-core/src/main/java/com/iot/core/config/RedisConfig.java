package com.iot.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * <p>
 * 配置 RedisTemplate 的序列化方式：
 * - Key 使用 StringRedisSerializer，便于查看和管理
 * - Value 使用 Jackson2JsonRedisSerializer，支持对象序列化并携带类型信息
 * </p>
 *
 * @author IoT Team
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate Bean
     * <p>
     * 自定义序列化方式，确保存入 Redis 的数据可读且支持 Java 对象的自动序列化/反序列化。
     * 同时注册 JavaTimeModule 以支持 Java 8 日期时间类型的序列化。
     * </p>
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 使用 StringRedisSerializer 序列化 key
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // 使用 Jackson2JsonRedisSerializer 序列化 value
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册 Java 8 日期时间模块，支持 LocalDateTime 等类型的序列化
        objectMapper.registerModule(new JavaTimeModule());
        // 启用默认类型信息，序列化时在 JSON 中写入类型信息，便于反序列化
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
