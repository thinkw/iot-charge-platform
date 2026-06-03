package com.iot.common.constant;

/**
 * 设备相关常量
 * <p>
 * 定义充电设备的通用配置常量，包括心跳检测间隔、超时时间、
 * 设备协议类型、设备状态变更等相关参数。
 * </p>
 *
 * @author IoT Team
 */
public class DeviceConstants {

    /** 设备心跳间隔（秒）：建议设备每30秒发送一次心跳 */
    public static final int HEARTBEAT_INTERVAL = 30;

    /** 设备超时时间（秒）：超过90秒未收到心跳则认为设备离线 */
    public static final int HEARTBEAT_TIMEOUT = 90;

    /** 设备最大重连次数 */
    public static final int MAX_RECONNECT_TIMES = 3;

    /** 设备重连间隔（秒） */
    public static final int RECONNECT_INTERVAL = 10;

    /** 设备编号前缀 */
    public static final String DEVICE_SN_PREFIX = "CHG";

    /** 默认设备端口数量 */
    public static final int DEFAULT_PORT_COUNT = 2;

    /** 最大设备端口数量 */
    public static final int MAX_PORT_COUNT = 10;

    /** 设备名称最大长度 */
    public static final int MAX_DEVICE_NAME_LENGTH = 64;

    /** 设备地址最大长度 */
    public static final int MAX_DEVICE_ADDRESS_LENGTH = 256;

    /**
     * 私有构造方法，防止实例化
     */
    private DeviceConstants() {
    }

    // ==================== Redis Key 常量 ====================

    /** Redis 设备在线状态 Key 前缀，格式：device:status:{sn} */
    public static final String REDIS_KEY_DEVICE_STATUS = "device:status:";

    /** Redis 设备实时数据 Key 前缀，格式：device:data:{sn} */
    public static final String REDIS_KEY_DEVICE_DATA = "device:data:";

    /** Redis 充电桩状态缓存 Key 前缀（与设备状态 Key 统一） */
    public static final String REDIS_KEY_CHARGER_STATUS = "device:status:";
}
