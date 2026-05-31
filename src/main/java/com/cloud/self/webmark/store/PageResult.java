package com.cloud.self.webmark.store;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果，替代 MyBatis-Plus 的 IPage
 */
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size > 0 ? (long) Math.ceil((double) total / size) : 0;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    public <R> PageResult<R> convert(Function<T, R> mapper) {
        List<R> converted = records.stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(converted, total, current, size);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0, 1, 10);
    }
}
