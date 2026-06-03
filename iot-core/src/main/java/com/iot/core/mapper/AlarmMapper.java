package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 告警记录 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * 统计类查询通过 @Select 注解直接编写 SQL 聚合查询。
 * </p>
 *
 * @author IoT Team
 */
@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {

    /**
     * 按告警类型统计未处理告警数量
     * <p>
     * 使用 SQL GROUP BY 替代内存聚合，避免加载全部未处理告警。
     * </p>
     *
     * @return List&lt;Map&gt; 每项包含 alarm_type 和 cnt 两个字段
     */
    @Select("SELECT alarm_type, COUNT(*) AS cnt FROM alarm WHERE status = 0 GROUP BY alarm_type")
    List<Map<String, Object>> countUnhandledByType();

    /**
     * 按告警级别统计未处理告警数量
     *
     * @return List&lt;Map&gt; 每项包含 alarm_level 和 cnt 两个字段
     */
    @Select("SELECT alarm_level, COUNT(*) AS cnt FROM alarm WHERE status = 0 GROUP BY alarm_level")
    List<Map<String, Object>> countUnhandledByLevel();
}
