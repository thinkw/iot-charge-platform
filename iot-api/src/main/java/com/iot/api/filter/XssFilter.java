package com.iot.api.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * XSS 跨站脚本攻击过滤器
 * <p>
 * 对用户输入的请求参数进行 HTML 特殊字符转义，防止 XSS 攻击。
 * 通过包装 HttpServletRequest，在获取参数时自动进行转义处理，
 * 避免恶意脚本在页面中执行。
 * </p>
 */
@Slf4j
@Component
public class XssFilter implements Filter {

    /**
     * 过滤方法
     * <p>
     * 使用自定义的 XssHttpServletRequestWrapper 包装原始请求，
     * 在获取参数时自动进行 HTML 转义。
     * </p>
     *
     * @param request  Servlet 请求
     * @param response Servlet 响应
     * @param chain    过滤器链
     * @throws IOException      I/O 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 使用包装类替换原始请求，实现参数转义
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(httpRequest);
        chain.doFilter(xssRequest, response);
    }

    /**
     * XSS 安全请求包装器
     * <p>
     * 继承 HttpServletRequestWrapper，重写获取参数的相关方法，
     * 在返回参数值之前对 HTML 特殊字符进行转义。
     * </p>
     */
    private static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        /**
         * 构造方法
         *
         * @param request 原始 HTTP 请求
         */
        public XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        /**
         * 获取单个参数值（已转义）
         *
         * @param name 参数名
         * @return 转义后的参数值
         */
        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        /**
         * 获取参数值数组（已转义）
         *
         * @param name 参数名
         * @return 转义后的参数值数组
         */
        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] sanitizedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitizedValues[i] = sanitize(values[i]);
            }
            return sanitizedValues;
        }

        /**
         * 获取参数 Map（值已转义）
         *
         * @return 转义后的参数 Map
         */
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> parameterMap = super.getParameterMap();
            Map<String, String[]> sanitizedMap = new HashMap<>();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String[] values = entry.getValue();
                String[] sanitizedValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    sanitizedValues[i] = sanitize(values[i]);
                }
                sanitizedMap.put(entry.getKey(), sanitizedValues);
            }
            return sanitizedMap;
        }

        /**
         * 获取请求头（已转义）
         *
         * @param name 请求头名
         * @return 转义后的请求头值
         */
        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return sanitize(value);
        }

        /**
         * HTML 特殊字符转义
         * <p>
         * 将 HTML 敏感字符替换为对应的转义实体：
         * & → &amp;、&lt; → &lt;、&gt; → &gt;、" → &quot;、' → &#x27;
         * </p>
         *
         * @param input 原始字符串
         * @return 转义后的字符串，null 或空值直接返回
         */
        private String sanitize(String input) {
            if (!StringUtils.hasText(input)) {
                return input;
            }
            return input
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll("\"", "&quot;")
                    .replaceAll("'", "&#x27;");
        }
    }
}
