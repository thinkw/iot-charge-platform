package com.iot.core.mapper;

import com.iot.core.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 *
 * @author IoT Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
