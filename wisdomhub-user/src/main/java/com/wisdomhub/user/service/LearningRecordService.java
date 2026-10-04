package com.wisdomhub.user.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.user.domain.vo.LearningRecordVO;

public interface LearningRecordService {

    void add(String technologyName);

    void remove(String technologyName);

    void markMastered(String technologyName);

    void unmarkMastered(String technologyName);

    PageResponse<LearningRecordVO> list(Integer page, Integer pageSize);
}
