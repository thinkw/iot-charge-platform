package com.iot.core.service;

import java.util.Map;

/**
 * 设备管理服务接口
 * <p>
 * 提供设备全生命周期管理能力，包括设备鉴权、上下线处理、心跳维护、
 * 状态上报、实时数据更新、故障上报、指令下发以及状态机校验。
 * </p>
 *
 * @author IoT Team
 */
public interface DeviceService {

    /**
     * 验证设备凭证
     * <p>
     * 根据设备SN查询数据库，验证密钥是否匹配。
     * 验证通过后更新设备最后上线时间。
     * </p>
     *
     * @param sn     设备唯一序列号
     * @param secret 设备密钥
     * @return true 鉴权通过，false 鉴权失败
     */
    boolean authenticateDevice(String sn, String secret);

    /**
     * 处理设备上线
     * <p>
     * 将设备状态设置为空闲(IDLE)，更新 Redis 在线状态和 MySQL 数据库，
     * 记录设备日志，发送 RocketMQ 设备上线事件。
     * </p>
     *
     * @param sn 设备唯一序列号
     */
    void handleOnline(String sn);

    /**
     * 处理设备离线
     * <p>
     * 将设备状态设置为离线(OFFLINE)，更新 Redis 和 MySQL，
     * 记录设备日志，发送 RocketMQ 设备离线事件。
     * 如果设备当前处于充电中状态，则同时创建离线告警。
     * </p>
     *
     * @param sn 设备唯一序列号
     */
    void handleOffline(String sn);

    /**
     * 处理设备心跳
     * <p>
     * 更新 Redis 中设备的心跳时间戳，延长设备在线状态。
     * </p>
     *
     * @param sn 设备唯一序列号
     */
    void handleHeartbeat(String sn);

    /**
     * 处理设备状态上报
     * <p>
     * 设备主动上报状态变更（如从空闲变为充电中）。
     * 会进行状态机校验，非法状态转换将被拒绝。
     * </p>
     *
     * @param sn     设备唯一序列号
     * @param status 新状态码（参考 {@link com.iot.common.enums.DeviceStatusEnum}）
     * @param data   附加数据（电压、电流、功率、电量、温度等）
     */
    void handleStatusReport(String sn, int status, Map<String, Object> data);

    /**
     * 处理设备实时数据上报
     * <p>
     * 更新 Redis 中设备的实时运行数据（电压、电流、功率、已充电量、温度）。
     * 同时更新 MySQL charger 表中的实时数据字段。
     * </p>
     *
     * @param sn   设备唯一序列号
     * @param data 实时数据（voltage, current, power, energy, temperature）
     */
    void handleDataReport(String sn, Map<String, Object> data);

    /**
     * 处理设备故障上报
     * <p>
     * 设备主动上报故障信息，创建告警记录，更新设备状态为故障(FAULT)，
     * 发送 RocketMQ 告警事件通知运营后台。
     * </p>
     *
     * @param sn        设备唯一序列号
     * @param alarmType 告警类型（OVER_TEMP/OVER_VOLT/UNDER_VOLT/SHORT_CIRCUIT/LEAKAGE/COMM_ERROR）
     * @param alarmLevel 告警级别（1-一般，2-重要，3-紧急）
     * @param content   告警内容描述
     */
    void handleAlarmReport(String sn, String alarmType, int alarmLevel, String content);

    /**
     * 向指定设备下发指令
     * <p>
     * 通过 DeviceCommandSender（MQTT）向设备下发远程控制指令。
     * 如果设备不在线则返回失败。
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（START_CHARGE/STOP_CHARGE/RESTART/SET_PARAM）
     * @param params  指令参数
     * @return true 指令发送成功，false 发送失败或设备不在线
     */
    boolean sendCommand(String sn, String command, Map<String, Object> params);

    /**
     * 验证设备状态转换是否合法
     * <p>
     * 根据设备状态机规则检查当前状态是否允许变更为目标状态。
     * </p>
     *
     * @param currentStatus 当前状态码
     * @param targetStatus  目标状态码
     * @return true 状态转换合法，false 非法状态转换
     */
    boolean validateStatusTransition(int currentStatus, int targetStatus);

    /**
     * 定时检查心跳超时（由 @Scheduled 定时任务调用）
     * <p>
     * 扫描 Redis 中所有设备的心跳时间，将超过
     * {@link com.iot.common.constant.DeviceConstants#HEARTBEAT_TIMEOUT} 秒
     * 的设备标记为离线。
     * </p>
     */
    void checkHeartbeatTimeout();

    /**
     * 检查设备是否在线
     *
     * @param sn 设备唯一序列号
     * @return true 在线，false 离线
     */
    boolean isDeviceOnline(String sn);
}
