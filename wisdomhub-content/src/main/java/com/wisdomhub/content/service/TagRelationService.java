package com.wisdomhub.content.service;

import com.wisdomhub.content.domain.dto.TagRelationDTO;
import com.wisdomhub.content.domain.vo.TagRelationSyncVO;
import com.wisdomhub.content.domain.vo.TagRelationVO;

import java.util.List;

public interface TagRelationService {

    List<TagRelationVO> listByTag(Long tagId);

    /** 全量关系快照，供 knowledge 建图与对账 */
    List<TagRelationSyncVO> listAll();

    TagRelationVO create(TagRelationDTO dto);

    /** 审批落地用：source 由提案人类型决定，proposalId 用于溯源 */
    TagRelationVO createFromProposal(TagRelationDTO dto, int source, Long proposalId);

    void delete(Long relationId);
}
