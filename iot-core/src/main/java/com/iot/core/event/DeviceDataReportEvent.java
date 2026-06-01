package com.iot.core.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 设备数据上报事件
 * <p>
 * 当设备通过 MQTT 上报实时数据时，DeviceServiceImpl 发布此 Spring 事件。
 * ChargeService 通过 @EventListener 监听，触发充电进度推送。
 * </p>
 *
 * @author IoT Team
 */
@Getter
@AllArgsConstructor
public class DeviceDataReportEvent {

    /** 设备SN */
    private String sn;

    /** 充电桩ID */
    private Long chargerId;

    /** 上报的实时数据（voltage, current, power, energy, temperature） */
    private Map<String, Object> data;
}
