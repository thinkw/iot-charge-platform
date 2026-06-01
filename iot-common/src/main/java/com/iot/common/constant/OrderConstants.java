package com.iot.common.constant;

import java.math.BigDecimal;

/**
 * 订单相关常量
 * <p>
 * 定义充电订单的通用配置常量，包括订单超时时间、计费参数、
 * 订单编号前缀、默认费率等相关参数。
 * </p>
 *
 * @author IoT Team
 */
public class OrderConstants {

    /** 订单编号前缀 */
    public static final String ORDER_NO_PREFIX = "ORD";

    /** 订单支付超时时间（分钟）：超过30分钟未支付则自动取消 */
    public static final int PAY_TIMEOUT_MINUTES = 30;

    /** 订单完成后的保留时间（天）：完成后保留7天 */
    public static final int ORDER_RETENTION_DAYS = 7;

    /** 默认电价（元/度） */
    public static final BigDecimal DEFAULT_PRICE_PER_KWH = new BigDecimal("0.80");

    /** 默认服务费（元） */
    public static final BigDecimal DEFAULT_SERVICE_FEE = new BigDecimal("0.50");

    /** 最小充电金额（元） */
    public static final BigDecimal MIN_CHARGE_AMOUNT = new BigDecimal("0.01");

    /** 最大充电金额（元） */
    public static final BigDecimal MAX_CHARGE_AMOUNT = new BigDecimal("9999.99");

    /** 订单金额小数位数 */
    public static final int AMOUNT_SCALE = 2;

    /** 充电电量小数位数 */
    public static final int ENERGY_SCALE = 3;

    /** 订单备注最大长度 */
    public static final int MAX_REMARK_LENGTH = 256;

    /**
     * 私有构造方法，防止实例化
     */
    private OrderConstants() {
    }
}
