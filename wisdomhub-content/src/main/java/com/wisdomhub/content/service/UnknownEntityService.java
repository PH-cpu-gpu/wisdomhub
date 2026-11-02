package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.UnknownEntityHitDTO;
import com.wisdomhub.content.domain.vo.UnknownEntityVO;

public interface UnknownEntityService {

    /**
     * 记录一篇文章抽出的未收录实体。明细按 (归一化名, 文章) 去重，
     * 只有真正新增的明细才会累加汇总计数；返回新增条数。
     */
    int recordHits(UnknownEntityHitDTO dto);

    /** 候选分页：promoted 为空不筛，true 只看已升级，false 只看未升级 */
    PageResponse<UnknownEntityVO> page(String keyword, Boolean promoted, Integer minExtractCount,
                                       Integer page, Integer pageSize);
}
