package com.iot.core.service;

import com.iot.common.model.CommandResult;

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
     * 向指定设备下发指令（v1 兼容接口，不带响应追踪）
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（如 START_CHARGE、STOP_CHARGE、RESTART）
     * @param params  指令参数
     * @return true 如果指令发送成功，false 如果设备不在线
     */
    boolean sendCommand(String sn, String command, Map<String, Object> params);

    /**
     * 向指定设备下发指令（v2 带响应追踪）
     * <p>
     * 生成唯一 commandId 用于指令-响应匹配和去重。
     * 指令详情注册到指令管理器用于后续追踪。
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（如 START_CHARGE、STOP_CHARGE）
     * @param params  指令参数
     * @param orderNo 关联订单号（可选）
     * @param userId  操作用户ID（可选）
     * @return commandId 如果发送成功，null 如果设备不在线
     */
    String sendCommand(String sn, String command, Map<String, Object> params, String orderNo, Long userId);

    /**
     * 向指定设备下发指令并同步等待响应（混合模式核心方法）
     * <p>
     * 发送指令后阻塞当前线程等待设备响应，超时后返回 TIMEOUT。
     * 用于需要确认设备执行结果的场景（如启桩、停桩）。
     * </p>
     *
     * @param sn        设备唯一序列号
     * @param command   指令类型（如 START_CHARGE、STOP_CHARGE）
     * @param params    指令参数
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     * @param timeoutMs 同步等待超时（毫秒）
     * @return CommandResult.SUCCESS 设备确认执行成功，
     *         CommandResult.DEVICE_ERROR 设备拒绝执行，
     *         CommandResult.TIMEOUT 同步等待超时（异步补偿继续）
     */
    CommandResult sendCommandAndWait(String sn, String command, Map<String, Object> params,
                                     String orderNo, Long userId, long timeoutMs);
}
