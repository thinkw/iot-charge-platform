package com.iot.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IoT Charging Platform — 虚拟充电桩模拟器入口
 * <p>
 * 独立运行的 Spring Boot 应用，模拟充电桩设备通过 MQTT 协议与平台通信。
 */
@SpringBootApplication(scanBasePackages = "com.iot")
public class IotSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotSimulatorApplication.class, args);
    }
}
