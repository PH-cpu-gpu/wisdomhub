package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.QuestionCreateDTO;
import com.wisdomhub.content.domain.vo.QuestionDetailVO;
import com.wisdomhub.content.domain.vo.QuestionListItemVO;

public interface QuestionService {

    /** 提问。创建类不幂等（17.3），重复提交真的会建两条。返回问题 ID */
    Long createQuestion(QuestionCreateDTO dto);

    /**
     * 问题列表：分页 + 排序（new=提问时间倒序 / hot=回答数倒序）。
     * 只返回未删除的问题；不返回 content（TEXT 正文不跟着列表走）
     */
    PageResponse<QuestionListItemVO> listQuestions(Integer page, Integer pageSize, String sort);

    /** 问题详情。未删除才可见，游客可读；登录用户多一个 favorited 布尔 */
    QuestionDetailVO getQuestionDetail(Long questionId);

    /** 收藏问题。设置状态类操作（17.3），幂等，返回最新收藏数 */
    int favorite(Long questionId);

    /** 取消收藏。幂等：没收藏过也返回 200 */
    int unfavorite(Long questionId);

    /** 采纳答案。只有提问者本人；回答必须属于该问题。再采纳另一条就是改采纳 */
    void acceptAnswer(Long questionId, Long answerId);

    /** 取消采纳。只有「当前采纳的正是这一条」才会清空，否则不动（幂等） */
    void cancelAccept(Long questionId, Long answerId);

    /**
     * 删除问题：仅提问者本人。级联软删该问题下的全部回答（连同这些回答的评论），
     * 并物理删掉关联行（answer_like / question_favorite）
     */
    void deleteQuestion(Long questionId);

    /** 我的问题收藏分页，按收藏时间倒序 */
    PageResponse<QuestionListItemVO> listMyFavoriteQuestions(Integer page, Integer pageSize);
}
