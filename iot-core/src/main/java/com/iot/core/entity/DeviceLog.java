package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备日志实体
 * <p>
 * 注意：本实体不继承 BaseEntity，因为设备日志表只有 id 和 create_time，没有 update_time 字段。
 * </p>
 *
 * @author IoT Team
 */
@Data
@TableName("device_log")
public class DeviceLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 充电桩ID
     */
    private Long chargerId;

    /**
     * 设备SN
     */
    private String sn;

    /**
     * 事件类型：ONLINE/OFFLINE/STATUS_CHANGE/COMMAND/FAULT
     */
    private String eventType;

    /**
     * 事件内容(JSON格式)
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
