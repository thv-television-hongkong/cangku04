package com.campus.charity.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页返回结构（列表接口全部走分页，保证查询响应时间 <= 2s）。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(converter).collect(Collectors.toList()));
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(current);
        result.setSize(size);
        result.setPages(size == 0 ? 0 : (total + size - 1) / size);
        return result;
    }
}
