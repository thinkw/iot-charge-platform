package com.iot.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IoT Charging Platform — HTTP API 入口
 * <p>
 * 负责启动 Spring Boot 应用，提供 RESTful API、安全认证等能力。
 * 自动扫描 com.iot 包下的所有组件（含 iot-core、iot-common）。
 * 启用定时任务调度，支持 DashboardPushScheduler 等定时推送。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.iot")
public class IotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotApiApplication.class, args);
    }
}
