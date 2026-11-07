package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.AnswerCreateDTO;
import com.wisdomhub.content.domain.vo.AnswerVO;

public interface AnswerService {

    /** 回答分页：被采纳的那条置顶，其余按发表时间升序。问题不存在/已删 → 404 */
    PageResponse<AnswerVO> listAnswers(Long questionId, Integer page, Integer pageSize);

    /** 回答问题。问题必须存在且未删。返回回答 ID */
    Long createAnswer(Long questionId, AnswerCreateDTO dto);

    /**
     * 删除回答：仅回答者本人。若这条正是被采纳的答案，同时清空问题的 accepted_answer_id；
     * 互动数据由 interaction 消费 target.deleted 清理
     */
    void deleteAnswer(Long answerId);
}
