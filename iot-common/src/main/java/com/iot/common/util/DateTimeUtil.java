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
}
