package com.wisdomhub.common.web.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.common.core.result.PageResponse;

public final class PageUtil {

    private PageUtil() {
    }

    // 把MP的Ipage转化为PageResponse
    public static <T> PageResponse<T> toResponse(IPage<T> page) {
        return PageResponse.<T>builder()
                .total(Math.toIntExact(page.getTotal()))
                .pages(Math.toIntExact(page.getPages()))
                .pageSize(Math.toIntExact(page.getSize()))
                .page(Math.toIntExact(page.getCurrent()))
                .items(page.getRecords())
                .build();
    }
}
