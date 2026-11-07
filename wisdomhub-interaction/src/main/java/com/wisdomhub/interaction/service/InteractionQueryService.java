package com.wisdomhub.interaction.service;

import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.domain.vo.InteractionStateVO;

public interface InteractionQueryService {

    InteractionStateVO getState(Integer targetType, Long targetId);

    InteractionCountBatchVO batchCounts(InteractionCountBatchDTO request);
}
