package com.wisdomhub.common.web.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.result.PageResponse;

import java.util.List;

public final class PageUtil {

    private PageUtil() {
    }

    /** 页码从 1 起，每页默认 10、上限 100，超了按 100 处理 */
    public static <T> Page<T> of(Integer page, Integer pageSize) {
        int current = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        return new Page<>(current, size);
    }

    /** 把 MP 的 IPage 转化为 PageResponse */
    public static <T> PageResponse<T> toResponse(IPage<T> page) {
        return toResponse(page, page.getRecords());
    }

    /** 元素类型与 IPage 的记录类型不同时用这个：分页元数据取自 page，items 单独给 */
    public static <T, R> PageResponse<R> toResponse(IPage<T> page, List<R> items) {
        return PageResponse.<R>builder()
                .total(Math.toIntExact(page.getTotal()))
                .pages(Math.toIntExact(page.getPages()))
                .pageSize(Math.toIntExact(page.getSize()))
                .page(Math.toIntExact(page.getCurrent()))
                .items(items)
                .build();
    }
}
