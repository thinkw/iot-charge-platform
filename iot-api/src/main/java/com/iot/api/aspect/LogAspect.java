package com.iot.api.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 日志切面
 * <p>
 * 对 com.iot.api.controller 包下的所有方法进行环绕增强，
 * 自动记录方法调用参数、执行时间和执行结果，便于问题排查和性能监控。
 * </p>
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /**
     * 环绕通知 — 记录控制器方法调用日志
     * <p>
     * 拦截 controller 包下的所有方法，在方法执行前后记录日志：
     * - 方法名和请求参数
     * - 方法执行耗时
     * - 方法返回结果或异常信息
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

        // 将参数列表拼接为字符串
        String params = Arrays.stream(joinPoint.getArgs())
                .map(arg -> arg != null ? arg.toString() : "null")
                .collect(Collectors.joining(", "));

        log.info("[请求] {}.{} - 参数: {}", className, methodName, params);

        long startTime = System.currentTimeMillis();

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[响应] {}.{} - 耗时: {}ms, 返回: {}", className, methodName, elapsed, result);

            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[异常] {}.{} - 耗时: {}ms, 异常信息: {}", className, methodName, elapsed, e.getMessage());
            throw e;
        }
    }
}
