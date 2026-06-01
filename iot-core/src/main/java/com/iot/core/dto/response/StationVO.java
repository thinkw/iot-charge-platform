package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 充电站列表 VO
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationVO {

    /** 主键ID */
    private Long id;

    /** 充电站名称 */
    private String name;

    /** 详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 营业时间 */
    private String businessHours;

    /** 联系电话 */
    private String contact;

    /** 状态：0-暂停营业，1-营业中，2-维护中 */
    private Integer status;

    /** 可用充电桩数量（状态为空闲的桩数） */
    private Integer availableCount;

    /** 该站最低电价（元/kWh），用于按价格排序 */
    private BigDecimal minPrice;

    /** 距离（km），仅在按距离排序时填充，用户未提供坐标时为空 */
    private Double distance;
}
