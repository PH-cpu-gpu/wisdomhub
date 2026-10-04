package com.wisdomhub.user.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.user.domain.vo.BrowseHistoryVO;

public interface BrowseHistoryService {

    void record(Long articleId);

    PageResponse<BrowseHistoryVO> list(Integer page, Integer pageSize);

    void delete(Long articleId);

    void clear();
}
