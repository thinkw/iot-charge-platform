package com.iot.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源配置（CORS）
 * <p>
 * 配置跨域资源共享策略，允许前端应用跨域访问后端 API。
 * 开发模式下放开所有来源，生产环境应限制为具体的域名列表。
 * </p>
 */
@Configuration
public class CorsConfig {

    /**
     * WebMvcConfigurer Bean
     * <p>
     * 添加 CORS 映射规则：
     * - 允许的来源：全部（开发模式，使用 allowedOriginPatterns 支持凭据）
     * - 允许的 HTTP 方法：GET、POST、PUT、DELETE、OPTIONS
     * - 允许的请求头：全部
     * - 允许携带凭证（Cookie、Authorization 头等）
     * </p>
     *
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
