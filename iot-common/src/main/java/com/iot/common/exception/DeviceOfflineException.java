package com.iot.common.exception;

/**
 * 设备离线异常
 * <p>
 * 当操作的目标设备处于离线状态时抛出此异常。
 * 例如：下发充电指令时设备离线、查询设备状态时设备无响应等。
 * 默认错误码为 409（冲突）。
 * </p>
 *
 * @author IoT Team
 */
public class DeviceOfflineException extends BusinessException {

    /**
     * 构造方法（指定设备标识）
     *
     * @param deviceId 设备ID或SN（设备序列号），将拼接到异常消息中
     */
    public DeviceOfflineException(String deviceId) {
        super(409, "设备已离线: " + deviceId);
    }

    /**
     * 构造方法（指定错误码和设备标识）
     *
     * @param code     错误码
     * @param deviceId 设备ID或SN（设备序列号），将拼接到异常消息中
     */
    public DeviceOfflineException(int code, String deviceId) {
        super(code, "设备已离线: " + deviceId);
    }
}
