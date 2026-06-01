package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alarm")
public class Alarm extends BaseEntity {

    /**
     * 充电桩ID
     */
    private Long chargerId;

    /**
     * 充电站ID
     */
    private Long stationId;

    /**
     * 告警类型：OVER_TEMP/OVER_VOLT/UNDER_VOLT/SHORT_CIRCUIT/LEAKAGE/OFFLINE/COMM_ERROR
     */
    private String alarmType;

    /**
     * 告警级别：1-一般，2-重要，3-紧急
     */
    private Integer alarmLevel;

    /**
     * 告警内容
     */
    private String content;

    /**
     * 状态：0-未处理，1-已处理
     */
    private Integer status;

    /**
     * 处理人ID
     */
    private Long handlerId;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 处理备注
     */
    private String handleNote;
}
