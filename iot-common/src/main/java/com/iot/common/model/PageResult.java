package com.iot.common.model;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果封装类
 * <p>
 * 用于分页查询的统一响应格式，包含数据列表、总记录数、当前页码、每页大小和总页数。
 * 总页数由总记录数和每页大小自动计算得出。
 * </p>
 *
 * @param <T> 数据类型
 * @author IoT Team
 */
public class PageResult<T> {

    /** 数据列表 */
    private List<T> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从1开始） */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /** 总页数（由 total 和 size 计算得出） */
    private Integer pages;

    /**
     * 无参构造方法
     */
    public PageResult() {
        this.records = Collections.emptyList();
    }

    /**
     * 全参构造方法
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页大小
     */
    public PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = computePages(total, size);
    }

    /**
     * 静态工厂方法，便捷创建分页结果
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param page    当前页码
     * @param size    每页大小
     * @param <T>     数据类型
     * @return PageResult 实例
     */
    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return new PageResult<>(records, total, page, size);
    }

    /**
     * 计算总页数
     *
     * @param total 总记录数
     * @param size  每页大小
     * @return 总页数
     */
    private static Integer computePages(Long total, Integer size) {
        if (total == null || size == null || size <= 0 || total <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    // ==================== Getters & Setters ====================

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records != null ? records : Collections.emptyList();
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
        this.pages = computePages(total, this.size);
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
        this.pages = computePages(this.total, size);
    }

    public Integer getPages() {
        return pages;
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "records.size=" + (records != null ? records.size() : 0) +
                ", total=" + total +
                ", page=" + page +
                ", size=" + size +
                ", pages=" + pages +
                '}';
    }
}
