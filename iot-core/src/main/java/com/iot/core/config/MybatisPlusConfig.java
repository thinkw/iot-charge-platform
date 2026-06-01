package com.iot.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置分页插件和 Mapper 扫描路径。
 * MapperScan 扫描 com.iot.core.mapper 包下的所有 Mapper 接口。
 * </p>
 *
 * @author IoT Team
 */
@Configuration
@MapperScan("com.iot.core.mapper")
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 分页插件
     * <p>
     * 使用 PaginationInnerInterceptor 实现 MySQL 数据库的分页功能，
     * 自动拦截带有 Page 参数的方法并生成分页 SQL。
     * </p>
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加 MySQL 分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
