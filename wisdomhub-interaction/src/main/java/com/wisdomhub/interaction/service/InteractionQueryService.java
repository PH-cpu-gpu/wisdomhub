package com.wisdomhub.interaction.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.vo.FavoriteItemVO;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.domain.vo.InteractionStateVO;

public interface InteractionQueryService {

    InteractionStateVO getState(Integer targetType, Long targetId);

    InteractionCountBatchVO batchCounts(InteractionCountBatchDTO request);

    /** 当前用户收藏的一页 (targetType,targetId)，按收藏时间倒序；targetType 为空则不限类型 */
    PageResponse<FavoriteItemVO> pageFavorites(Integer targetType, Integer page, Integer pageSize);
}
