package com.iot.simulator.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模拟器配置类
 * <p>
 * 读取 application.yml 中的 MQTT 连接配置，提供 MQTT 连接参数 Bean，
 * 供模拟器启动时连接 MQTT 服务器使用。
 * </p>
 */
@Slf4j
@Configuration
public class SimulatorConfig {

    @Value("${mqtt.broker}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.connection-timeout}")
    private int connectionTimeout;

    @Value("${mqtt.keep-alive-interval}")
    private int keepAliveInterval;

    /**
     * MQTT 连接参数 Bean
     * <p>
     * 封装从配置文件读取的 MQTT 连接信息，包含 Broker URL、客户端 ID、
     * 连接超时时间和心跳间隔。供模拟器的 MQTT 客户端在建立连接时使用。
     * </p>
     *
     * @return MqttConnectionParams 实例
     */
    @Bean
    public MqttConnectionParams mqttConnectionParams() {
        log.info("初始化 MQTT 连接参数 - broker: {}, clientId: {}", brokerUrl, clientId);
        MqttConnectionParams params = new MqttConnectionParams();
        params.setBrokerUrl(brokerUrl);
        params.setClientId(clientId);
        params.setConnectionTimeout(connectionTimeout);
        params.setKeepAliveInterval(keepAliveInterval);
        return params;
    }

    /**
     * MQTT 连接参数内部类
     * <p>
     * 封装 MQTT Broker 的连接参数，供模拟器的 MQTT 客户端建立连接时使用。
     * 使用 Lombok @Data 自动生成 Getter/Setter/toString 等方法。
     * </p>
     */
    @Data
    public static class MqttConnectionParams {
        /** MQTT Broker 地址（如 tcp://localhost:1883） */
        private String brokerUrl;
        /** 客户端唯一标识 */
        private String clientId;
        /** 连接超时时间（秒） */
        private int connectionTimeout;
        /** 心跳保活间隔（秒） */
        private int keepAliveInterval;
    }
}
