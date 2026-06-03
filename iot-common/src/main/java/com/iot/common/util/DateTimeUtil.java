package com.iot.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 * <p>
 * 提供常用的日期时间处理功能，包括：
 * - 获取当前时间
 * - 日期时间的格式化与解析
 * - 常用的日期时间格式常量
 * </p>
 *
 * @author IoT Team
 */
public class DateTimeUtil {

    /** 默认日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 标准日期格式：yyyy-MM-dd */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 紧凑日期时间格式：yyyyMMddHHmmss */
    public static final String COMPACT_PATTERN = "yyyyMMddHHmmss";

    /** 默认 DateTimeFormatter */
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);

    /**
     * 私有构造方法，防止实例化
     */
    private DateTimeUtil() {
    }

    /**
     * 获取当前日期时间
     *
     * @return 当前 LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 将 LocalDateTime 格式化为指定格式的字符串
     *
     * @param time    待格式化的时间
     * @param pattern 日期时间格式，如 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime time, String pattern) {
        if (time == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return time.format(formatter);
    }

    /**
     * 将 LocalDateTime 格式化为默认格式的字符串（yyyy-MM-dd HH:mm:ss）
     *
     * @param time 待格式化的时间
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.format(DEFAULT_FORMATTER);
    }

    /**
     * 将字符串解析为 LocalDateTime
     *
     * @param dateStr 日期时间字符串
     * @param pattern 日期时间格式，如 "yyyy-MM-dd HH:mm:ss"
     * @return 解析后的 LocalDateTime
     */
    public static LocalDateTime parse(String dateStr, String pattern) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(dateStr, formatter);
    }

    /**
     * 将字符串解析为 LocalDateTime（使用默认格式 yyyy-MM-dd HH:mm:ss）
     *
     * @param dateStr 日期时间字符串
     * @return 解析后的 LocalDateTime
     */
    public static LocalDateTime parse(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateStr, DEFAULT_FORMATTER);
    }

    /**
     * 解析 Controller 传入的时间参数
     * <p>
     * 处理 Controller 层的时间字符串，支持以下格式：
     * - ISO 8601 格式（含 'T' 分隔符，如 2026-06-01T12:00:00）
     * - 标准格式（yyyy-MM-dd HH:mm:ss）
     * 空白或 null 字符串返回 null，解析失败返回 null 并记录警告日志。
     * </p>
     *
     * @param timeStr 时间字符串
     * @return 解析后的 LocalDateTime，解析失败时为 null
     */
    public static LocalDateTime parseControllerParam(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            // 统一替换 ISO 格式中的 T 分隔符，截取前 19 个字符
            String cleaned = timeStr.replace("T", " ").trim();
            if (cleaned.length() >= 19) {
                cleaned = cleaned.substring(0, 19);
            }
            return LocalDateTime.parse(cleaned, DEFAULT_FORMATTER);
        } catch (Exception e) {
            // 解析失败，调用方自行处理
            return null;
        }
    }
}
