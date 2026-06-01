package com.iot.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IoT Charging Platform — 虚拟充电桩模拟器入口
 * <p>
 * 独立运行的 Spring Boot 非 Web 应用，通过 MQTT 协议连接平台，
 * 模拟充电桩设备的注册、心跳、状态上报、充电数据和故障上报等行为。
 * </p>
 * <p>
 * 启动后进入命令行交互模式，支持批量创建和管理虚拟充电桩。
 * 本模块不依赖 iot-core，仅依赖 iot-common 和 Eclipse Paho MQTT 客户端。
 * </p>
 *
 * @author IoT Team
 */
@SpringBootApplication(scanBasePackages = "com.iot.simulator")
public class IotSimulatorApplication {

    public static void main(String[] args) {
        // 非 Web 模式启动（纯命令行交互）
        SpringApplication app = new SpringApplication(IotSimulatorApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}
