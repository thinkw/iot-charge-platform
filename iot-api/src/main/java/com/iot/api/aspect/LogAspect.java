package com.iot.api.aspect;

import cn.hutool.json.JSONUtil;
import com.iot.api.security.SecurityUtil;
import com.iot.common.model.BaseEntity;
import com.iot.core.entity.OperationLog;
import com.iot.core.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 日志切面
 * <p>
 * 对 com.iot.api.controller 包下的所有方法进行环绕增强，
 * 自动记录方法调用日志到控制台，并异步持久化到 operation_log 表，
 * 用于安全审计和问题排查。
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final OperationLogService operationLogService;

    /**
     * 环绕通知 — 记录控制器方法调用日志
     * <p>
     * 拦截 controller 包下的所有方法，在方法执行前后记录日志：
     * - 控制台输出：方法名、参数、耗时、结果
     * - 数据库持久化：通过 OperationLogService 异步写入 operation_log 表
     * </p>
     * <p>
     * 持久化失败不影响业务接口正常返回。
     * </p>
     *
     * @param joinPoint 连接点（被拦截的方法）
     * @return 方法执行结果
     * @throws Throwable 被拦截方法抛出的异常
     */
    @Around("execution(* com.iot.api.controller..*.*(..))")
    public Object aroundControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 将参数列表拼接为字符串（排除文件上传等不可序列化参数）
        String params = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletRequest))
                .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletResponse))
                .map(arg -> {
                    try {
                        return arg != null ? arg.toString() : "null";
                    } catch (Exception e) {
                        return "[unserializable]";
                    }
                })
                .collect(Collectors.joining(", "));
        // 截断过长参数
        if (params.length() > 1000) {
            params = params.substring(0, 1000) + "...";
        }

        log.info("[请求] {}.{} - 参数: {}", className, methodName, params);

        long startTime = System.currentTimeMillis();

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[响应] {}.{} - 耗时: {}ms", className, methodName, elapsed);

            // 异步持久化操作日志（成功）
            saveOperationLog(className, methodName, params, 1, null, elapsed);

            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[异常] {}.{} - 耗时: {}ms, 异常信息: {}", className, methodName, elapsed, e.getMessage());

            // 异步持久化操作日志（失败）
            saveOperationLog(className, methodName, params, 0, e.getMessage(), elapsed);

            throw e;
        }
    }

    /**
     * 构建并异步保存操作日志
     *
     * @param className  控制器类名
     * @param methodName 方法名
     * @param params     请求参数
     * @param status     操作状态：0-失败，1-成功
     * @param errorMsg   错误信息（成功时为null）
     * @param costTime   执行耗时（毫秒）
     */
    private void saveOperationLog(String className, String methodName, String params,
                                   int status, String errorMsg, long costTime) {
        try {
            // 获取当前登录用户ID
            Long userId = null;
            String username = null;
            try {
                userId = SecurityUtil.getCurrentUserId();
            } catch (Exception ignored) {
                // 未登录用户（如登录/注册接口）
            }

            // 获取请求IP
            String ip = getClientIp();

            // 提取模块名（从类名前缀，如 AdminStationController → "AdminStation"）
            String module = className.replace("Controller", "");

            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setModule(module);
            log.setOperation(methodName);
            log.setMethod(className + "." + methodName);
            log.setParams(params);
            log.setIp(ip);
            log.setStatus(status);
            log.setErrorMsg(errorMsg);
            log.setCostTime(costTime);

            operationLogService.save(log);
        } catch (Exception e) {
            // 日志保存失败不影响业务
            log.warn("[操作日志] 保存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取客户端真实IP
     * <p>
     * 优先从 X-Forwarded-For、X-Real-IP 头获取，兼容反向代理场景。
     * </p>
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            HttpServletRequest request = attributes.getRequest();

            // 依次尝试从代理头获取
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // X-Forwarded-For 可能包含多个IP，取第一个
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
