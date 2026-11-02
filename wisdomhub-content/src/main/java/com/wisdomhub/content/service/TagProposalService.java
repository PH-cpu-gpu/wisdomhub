package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.TagProposalSubmitDTO;
import com.wisdomhub.content.domain.vo.TagProposalSubmitResult;
import com.wisdomhub.content.domain.vo.TagProposalValidateVO;
import com.wisdomhub.content.domain.vo.TagProposalVO;

public interface TagProposalService {

    TagProposalSubmitResult submit(TagProposalSubmitDTO dto);

    /** 提交前的校验；submit 内部调用，审批落地前也可复用 */
    TagProposalValidateVO validate(TagProposalSubmitDTO dto);

    PageResponse<TagProposalVO> page(Integer status, Integer proposalType, Integer page, Integer pageSize);

    TagProposalVO detail(Long id);

    void approve(Long id);

    void reject(Long id);

    /** 把超过保留期的待审提案置为过期，返回影响行数 */
    int expirePending();
}
