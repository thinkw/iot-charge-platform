package com.iot.common.constant;

/**
 * 系统级常量
 * <p>
 * 定义整个充电平台通用的系统级常量，包括编码格式、日期时间格式、
 * 默认分页参数、字符集等相关配置。
 * </p>
 *
 * @author IoT Team
 */
public class SystemConstants {

    /** 默认字符编码：UTF-8 */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /** 默认日期格式：yyyy-MM-dd */
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    /** 默认日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 默认时间格式：HH:mm:ss */
    public static final String TIME_FORMAT = "HH:mm:ss";

    /** 默认分页页码（从1开始） */
    public static final int DEFAULT_PAGE = 1;

    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大分页大小（防止一次查询过多数据） */
    public static final int MAX_PAGE_SIZE = 1000;

    /** 系统名称 */
    public static final String SYSTEM_NAME = "IoT充电管理平台";

    /** API版本号 */
    public static final String API_VERSION = "v1";

    /** 请求追踪ID的Header名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 私有构造方法，防止实例化
     */
    private SystemConstants() {
    }
}
