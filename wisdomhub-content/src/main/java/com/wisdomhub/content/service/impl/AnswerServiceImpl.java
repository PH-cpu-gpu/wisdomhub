package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.mq.event.AnswerCreatedEvent;
import com.wisdomhub.common.mq.event.payload.AnswerCreatedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.dto.AnswerCreateDTO;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.domain.vo.AnswerVO;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import com.wisdomhub.content.mq.TargetDeletedEventSender;
import com.wisdomhub.content.service.AnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerServiceImpl implements AnswerService {

    private static final String ANSWER_COUNT_COLUMN = "answer_count";

    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final EventPublisher eventPublisher;
    private final TargetDeletedEventSender targetDeletedEventSender;

    // ==================== 读 ====================

    @Override
    public PageResponse<AnswerVO> listAnswers(Long questionId, Integer page, Integer pageSize) {
        // 与 B1.25 的评论读路径不同，这里校验问题：路径嵌套在具体问题资源下，
        // 不校验则已删问题能靠它继续翻出回答，且不存在的 ID 会返回一个像样的空页
        Question question = requireVisibleQuestion(questionId);
        IPage<Answer> result = answerMapper.selectAnswerPage(PageUtil.of(page, pageSize),
                questionId, question.getAcceptedAnswerId(), QuestionConstants.NOT_DELETED);
        List<AnswerVO> items = result.getRecords().stream().map(this::toAnswerVO).toList();
        return PageUtil.toResponse(result, items);
    }

    // ==================== 写 ====================

    @Override
    @Transactional
    public Long createAnswer(Long questionId, AnswerCreateDTO dto) {
        Long userId = CurrentUserUtil.id();
        Question question = requireVisibleQuestion(questionId);
        Answer answer = Answer.builder()
                .questionId(questionId)
                .userId(userId)
                .content(dto.getContent())
                .deleted(QuestionConstants.NOT_DELETED.byteValue())
                .build();
        answerMapper.insert(answer);
        if (bumpAnswerCount(questionId, 1) == 0) {
            // 校验之后问题被并发删除：抛出去回滚这次插入
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "问题不存在");
        }
        sendAnswerCreatedEvent(answer.getId(), questionId, userId, question.getUserId());
        return answer.getId();
    }

    @Override
    @Transactional
    public void deleteAnswer(Long answerId) {
        Answer answer = requireVisibleAnswer(answerId);
        OwnerUtil.assertOwner(answer.getUserId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int deleted = answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .set(Answer::getDeleted, QuestionConstants.DELETED.byteValue())
                // update(null, wrapper) 不触发自动填充，逻辑删除要自己补 update_time
                .set(Answer::getUpdateTime, now)
                .eq(Answer::getId, answerId)
                .eq(Answer::getDeleted, QuestionConstants.NOT_DELETED));
        if (deleted == 0) {
            // 只有一种可能：requireVisibleAnswer 之后被并发删掉了。当作已删，幂等返回
            return;
        }
        bumpAnswerCount(answer.getQuestionId(), -1);

        // 删掉的正是被采纳的答案 → 清空采纳，否则详情会指向一条已删回答。
        // 条件里带 answer_id，避免把后来改成别的答案的采纳也一起清掉
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .set(Question::getAcceptedAnswerId, null)
                .set(Question::getUpdateTime, now)
                .eq(Question::getId, answer.getQuestionId())
                .eq(Question::getAcceptedAnswerId, answerId));

        targetDeletedEventSender.sendAfterCommit(ContentTargetTypeConstants.ANSWER, answerId);
    }

    // ==================== 事件 ====================

    /** 发失败只记日志：写已生效，抛出去会变成「接口 500 但回答其实发成功了」 */
    private void sendAnswerCreatedEvent(Long answerId, Long questionId, Long userId, Long questionAuthorId) {
        try {
            eventPublisher.send(new AnswerCreatedEvent(AnswerCreatedPayload.builder()
                    .answerId(answerId)
                    .questionId(questionId)
                    .userId(userId)
                    .questionAuthorId(questionAuthorId)
                    .build()));
        } catch (Exception e) {
            log.error("回答发表事件发送失败, answerId={}, questionId={}", answerId, questionId, e);
        }
    }

    // ==================== 共用 ====================

    /**
     * 问题必须存在且未删除。与 QuestionServiceImpl 里的同名私有方法同形 ——
     * 两处各自 8 行，第三次出现再抽公共组件
     */
    private Question requireVisibleQuestion(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "问题不存在");
        }
        return question;
    }

    private Answer requireVisibleAnswer(Long answerId) {
        Answer answer = answerMapper.selectById(answerId);
        if (answer == null || answer.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "回答不存在");
        }
        return answer;
    }

    /** 问题的回答数原子增减。delta 为负时带非负安全网 */
    private int bumpAnswerCount(Long questionId, int delta) {
        LambdaUpdateWrapper<Question> wrapper = new LambdaUpdateWrapper<Question>()
                .setSql(ANSWER_COUNT_COLUMN + " = " + ANSWER_COUNT_COLUMN + " + " + delta)
                .eq(Question::getId, questionId)
                .eq(Question::getDeleted, QuestionConstants.NOT_DELETED);
        if (delta < 0) {
            wrapper.ge(Question::getAnswerCount, 1);
        }
        return questionMapper.update(null, wrapper);
    }

    /** 与 QuestionServiceImpl.toAnswerVO 同形 */
    private AnswerVO toAnswerVO(Answer answer) {
        return AnswerVO.builder()
                .answerId(answer.getId())
                .questionId(answer.getQuestionId())
                .userId(answer.getUserId())
                .content(answer.getContent())
                .createTime(answer.getCreateTime())
                .build();
    }
}
