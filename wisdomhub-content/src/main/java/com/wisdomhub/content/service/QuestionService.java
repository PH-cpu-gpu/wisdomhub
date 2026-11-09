package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.QuestionCreateDTO;
import com.wisdomhub.content.domain.vo.QuestionDetailVO;
import com.wisdomhub.content.domain.vo.QuestionListItemVO;
import com.wisdomhub.content.domain.vo.QuestionSyncVO;

import java.time.LocalDateTime;
import java.util.List;

public interface QuestionService {

    /** 提问。创建类不幂等（17.3），重复提交真的会建两条。返回问题 ID */
    Long createQuestion(QuestionCreateDTO dto);

    /**
     * 问题列表：分页 + 排序（new=提问时间倒序 / hot=回答数倒序）。
     * 只返回未删除的问题；不返回 content（TEXT 正文不跟着列表走）
     */
    PageResponse<QuestionListItemVO> listQuestions(Integer page, Integer pageSize, String sort);

    /** 按 ID 批量取问题摘要（收藏页回填用）；已删除或不存在的不返回 */
    List<QuestionListItemVO> listBrief(List<Long> questionIds);

    /** 问题详情。未删除才可见，游客可读 */
    QuestionDetailVO getQuestionDetail(Long questionId);

    /** 采纳答案。只有提问者本人；回答必须属于该问题。再采纳另一条就是改采纳 */
    void acceptAnswer(Long questionId, Long answerId);

    /** 取消采纳。只有「当前采纳的正是这一条」才会清空，否则不动（幂等） */
    void cancelAccept(Long questionId, Long answerId);

    /** 删除问题：仅提问者本人。级联软删该问题下的全部回答；互动数据由 interaction 消费 target.deleted 清理 */
    void deleteQuestion(Long questionId);

    /** 对账分页：按 (updateTime, id) 游标，since 为 epoch 表示全量；**包含已删行**，供增量发 question.deleted */
    List<QuestionSyncVO> syncQuestions(LocalDateTime since, Long lastId, Integer size);

    /** 问题下所有回答 ID（含已删除），供 interaction 删除级联 */
    List<Long> listAnswerIds(Long questionId);
}
