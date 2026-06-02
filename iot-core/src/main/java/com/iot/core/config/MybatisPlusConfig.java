package com.iot.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置分页插件、乐观锁插件和 Mapper 扫描路径。
 * MapperScan 扫描 com.iot.core.mapper 包下的所有 Mapper 接口。
 * </p>
 *
 * @author IoT Team
 */
@Configuration
@MapperScan("com.iot.core.mapper")
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 插件链
     * <p>
     * 包含：
     * - PaginationInnerInterceptor：MySQL 分页拦截器
     * - OptimisticLockerInnerInterceptor：乐观锁拦截器，配合实体中 @Version 注解使用
     *   更新时自动在 WHERE 子句添加 version = ? 条件，防止并发覆盖
     * </p>
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁拦截器（必须注册，否则 @Version 字段会报 MP_OPTLOCK_VERSION_ORIGINAL 异常）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // MySQL 分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
