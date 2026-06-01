package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 充电站实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("station")
public class Station extends BaseEntity {

    /**
     * 充电站名称
     */
    private String name;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 营业时间，如 00:00-24:00
     */
    private String businessHours;

    /**
     * 联系电话
     */
    private String contact;

    /**
     * 状态：0-暂停营业，1-营业中，2-维护中
     */
    private Integer status;
}
