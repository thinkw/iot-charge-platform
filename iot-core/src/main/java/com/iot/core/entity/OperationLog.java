package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体
 * <p>
 * 记录系统中所有用户的后台操作行为，包括操作人、操作模块、
 * 请求方法、参数、执行结果、耗时等信息，用于安全审计和问题排查。
 * </p>
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型（方法名）
     */
    private String operation;

    /**
     * 请求方法全限定名（类名.方法名）
     */
    private String method;

    /**
     * 请求参数（JSON格式）
     */
    private String params;

    /**
     * 请求IP地址
     */
    private String ip;

    /**
     * 操作状态：0-失败，1-成功
     */
    private Integer status;

    /**
     * 错误信息（操作失败时记录）
     */
    private String errorMsg;

    /**
     * 执行耗时（毫秒）
     */
    private Long costTime;
}
