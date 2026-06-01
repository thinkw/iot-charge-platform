package com.iot.core.service;

import java.util.Map;

/**
 * 设备指令下发接口
 * <p>
 * 定义向设备下发远程指令的抽象接口。接口定义在 iot-core 层，
 * 具体实现（MQTT 下发）在 iot-access 层完成，遵循依赖倒置原则。
 * </p>
 *
 * @author IoT Team
 */
public interface DeviceCommandSender {

    /**
     * 向指定设备下发指令
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（如 START_CHARGE、STOP_CHARGE、RESTART）
     * @param params  指令参数
     * @return true 如果指令发送成功，false 如果设备不在线
     */
    boolean sendCommand(String sn, String command, Map<String, Object> params);
}
