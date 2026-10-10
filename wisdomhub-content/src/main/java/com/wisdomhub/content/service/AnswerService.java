package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.AnswerCreateDTO;
import com.wisdomhub.content.domain.vo.AnswerVO;

public interface AnswerService {

    /** 回答分页：被采纳的那条置顶，其余按发表时间升序。问题不存在/已删 → 404 */
    PageResponse<AnswerVO> listAnswers(Long questionId, Integer page, Integer pageSize);

    /** 回答问题。问题必须存在且未删。返回回答 ID */
    Long createAnswer(Long questionId, AnswerCreateDTO dto);

    /** 给回答点赞。设置状态类操作（17.3），幂等，返回最新点赞数 */
    int like(Long answerId);

    /** 取消点赞。幂等：没点过也返回 200 */
    int unlike(Long answerId);

    /**
     * 删除回答：仅回答者本人。级联软删这条回答下的评论；若这条正是被采纳的答案，
     * 同时清空问题的 accepted_answer_id（否则详情会指向一条已删回答），并物理删掉它的点赞关系行
     */
    void deleteAnswer(Long answerId);
}
