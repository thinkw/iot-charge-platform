package com.iot.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 设备离线与订单对账配置类
 * <p>
 * 统一管理设备离线检测、订单自动终止、服务费减免等时间阈值和折扣参数。
 * 所有配置项均有合理默认值，可在 application.yml 中按需覆盖。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "device.offline")
public class DeviceOfflineConfig {

    /** 心跳超时阈值（秒），超过此时间未收到心跳则认为设备离线，默认 90 秒 */
    private int heartbeatTimeoutSeconds = 90;

    /** 心跳超时扫描间隔（毫秒），定时任务扫描频率，默认 30 秒 */
    private long heartbeatScanIntervalMs = 30_000;

    /** 离线后终止订单延迟（秒），防网络抖动导致误终止，默认 120 秒 */
    private int terminateDelaySeconds = 120;

    /** 设备恢复后等待状态上报宽限期（秒），超时未上报则终止订单，默认 60 秒 */
    private int recoveryGraceSeconds = 60;

    /** 孤儿订单扫描间隔（毫秒），兜底定时任务扫描频率，默认 60 秒 */
    private long orphanScanIntervalMs = 60_000;

    /** 异常终止时服务费折扣率（0.0~1.0），1.0 表示全额收取，0.5 表示五折，默认 0.5 */
    private BigDecimal serviceFeeDiscount = new BigDecimal("0.5");
}
