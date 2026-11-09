package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.QuestionCreatedEvent;
import com.wisdomhub.common.mq.event.QuestionDeletedEvent;
import com.wisdomhub.common.mq.event.payload.QuestionDeletedPayload;
import com.wisdomhub.common.mq.event.payload.QuestionEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.dto.QuestionCreateDTO;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.domain.vo.AnswerVO;
import com.wisdomhub.content.domain.vo.QuestionDetailVO;
import com.wisdomhub.content.domain.vo.QuestionListItemVO;
import com.wisdomhub.content.domain.vo.QuestionSyncVO;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import com.wisdomhub.content.mq.TargetDeletedEventSender;
import com.wisdomhub.content.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {


    private static final int SYNC_DEFAULT_SIZE = 200;
    private static final int SYNC_MAX_SIZE = 500;

    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final EventPublisher eventPublisher;
    private final TargetDeletedEventSender targetDeletedEventSender;

    // ==================== 读 ====================

    @Override
    public PageResponse<QuestionListItemVO> listQuestions(Integer page, Integer pageSize, String sort) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getUserId, Question::getTitle, Question::getAnswerCount,
                        Question::getAcceptedAnswerId, Question::getCreateTime)
                .eq(Question::getDeleted, QuestionConstants.NOT_DELETED);
        applySort(wrapper, sort);

        Page<Question> result = questionMapper.selectPage(PageUtil.of(page, pageSize), wrapper);
        return PageUtil.toResponse(result, result.getRecords().stream().map(this::toListItem).toList());
    }

    /**
     * sort 白名单映射只写在这里，绝不把前端传的字符串拼进 SQL。
     */
    private void applySort(LambdaQueryWrapper<Question> wrapper, String sort) {
        String normalized = StrUtil.isBlank(sort) ? QuestionConstants.SORT_NEW : sort.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case QuestionConstants.SORT_NEW ->
                    wrapper.orderByDesc(Question::getCreateTime).orderByDesc(Question::getId);
            case QuestionConstants.SORT_HOT ->
                    wrapper.orderByDesc(Question::getAnswerCount).orderByDesc(Question::getId);
            // 白名单外一律 400：静默回退会掩盖前端拼错
            default -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "排序方式不合法");
        }
    }

    @Override
    public List<QuestionListItemVO> listBrief(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getUserId, Question::getTitle,
                                Question::getAnswerCount, Question::getAcceptedAnswerId,
                                Question::getCreateTime)
                        .in(Question::getId, questionIds)
                        .eq(Question::getDeleted, QuestionConstants.NOT_DELETED))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Override
    public QuestionDetailVO getQuestionDetail(Long questionId) {
        Question question = requireVisibleQuestion(questionId);
        Answer accepted = question.getAcceptedAnswerId() == null
                ? null
                : answerMapper.selectById(question.getAcceptedAnswerId());
        // 采纳回答被删时删除接口会清空 accepted_answer_id，这里再兜一层：绝不返回已删的正文
        if (accepted != null && accepted.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            accepted = null;
        }
        return QuestionDetailVO.builder()
                .questionId(question.getId())
                .userId(question.getUserId())
                .title(question.getTitle())
                .content(question.getContent())
                .answerCount(question.getAnswerCount())
                .acceptedAnswerId(question.getAcceptedAnswerId())
                .acceptedAnswer(accepted == null ? null : toAnswerVO(accepted))
                .createTime(question.getCreateTime())
                .build();
    }

    // ==================== 写 ====================

    @Override
    public Long createQuestion(QuestionCreateDTO dto) {
        Question question = Question.builder()
                .userId(CurrentUserUtil.id())
                .title(dto.getTitle())
                .content(dto.getContent())
                .answerCount(0)
                .deleted(QuestionConstants.NOT_DELETED.byteValue())
                .build();
        questionMapper.insert(question);
        publishQuestionCreated(question.getId());
        return question.getId();
    }

    @Override
    public void acceptAnswer(Long questionId, Long answerId) {
        Question question = requireVisibleQuestion(questionId);
        OwnerUtil.assertOwner(question.getUserId());
        requireAnswerOfQuestion(questionId, answerId);
        setAcceptedAnswer(questionId, answerId, null);
    }

    @Override
    public void cancelAccept(Long questionId, Long answerId) {
        Question question = requireVisibleQuestion(questionId);
        OwnerUtil.assertOwner(question.getUserId());
        // 只在「当前采纳的正是这一条」时清空：幂等，且传了别的 answerId 不会误撤别人的采纳
        setAcceptedAnswer(questionId, null, answerId);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long questionId) {
        Question question = requireVisibleQuestion(questionId);
        OwnerUtil.assertOwner(question.getUserId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .set(Question::getDeleted, QuestionConstants.DELETED.byteValue())
                .set(Question::getUpdateTime, now)
                .eq(Question::getId, questionId)
                .eq(Question::getDeleted, QuestionConstants.NOT_DELETED));

        // 级联软删该问题下的全部回答
        answerMapper.update(null, new LambdaUpdateWrapper<Answer>()
                .set(Answer::getDeleted, QuestionConstants.DELETED.byteValue())
                .set(Answer::getUpdateTime, now)
                .eq(Answer::getQuestionId, questionId)
                .eq(Answer::getDeleted, QuestionConstants.NOT_DELETED));

        // 本方法是 @Transactional，直接发会在回滚时留下「消息发了但问题还在」
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendEvent(new QuestionDeletedEvent(QuestionDeletedPayload.builder()
                        .questionId(questionId)
                        .userId(question.getUserId())
                        .build()));
            }
        });
        targetDeletedEventSender.sendAfterCommit(ContentTargetTypeConstants.QUESTION, questionId);
    }

    // ==================== 事件 ====================

    private void publishQuestionCreated(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            return;
        }
        sendEvent(new QuestionCreatedEvent(QuestionEventPayload.builder()
                .questionId(question.getId())
                .userId(question.getUserId())
                .title(question.getTitle())
                .content(question.getContent())
                .answerCount(question.getAnswerCount())
                .acceptedAnswerId(question.getAcceptedAnswerId())
                .createTime(question.getCreateTime())
                .updateTime(question.getUpdateTime())
                .build()));
    }

    /** 发失败只记日志：事务已提交，抛出去会变成「接口 500 但问题其实提问成功了」 */
    private void sendEvent(BaseEvent<?> event) {
        try {
            eventPublisher.send(event);
        } catch (Exception e) {
            log.error("问题事件发送失败, eventType={}", event.getEventType(), e);
        }
    }

    // ==================== 共用 ====================

    /** 问题必须存在且未删除，否则 404。*/
    private Question requireVisibleQuestion(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "问题不存在");
        }
        return question;
    }

    /** 采纳的目标必须存在、未删、且属于这个问题。不属于就当作不存在，不泄露别的回答的 ID */
    private void requireAnswerOfQuestion(Long questionId, Long answerId) {
        Answer answer = answerMapper.selectById(answerId);
        if (answer == null
                || answer.getDeleted() == QuestionConstants.DELETED.byteValue()
                || !questionId.equals(answer.getQuestionId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "回答不存在");
        }
    }

    /**
     * 采纳 / 取消采纳共用的 UPDATE。expectCurrent 非空时只在「当前采纳的正是它」时才生效，
     * 让取消操作天然幂等
     */
    private void setAcceptedAnswer(Long questionId, Long acceptedAnswerId, Long expectCurrent) {
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .set(Question::getAcceptedAnswerId, acceptedAnswerId)
                .set(Question::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Question::getId, questionId)
                .eq(Question::getDeleted, QuestionConstants.NOT_DELETED)
                .eq(expectCurrent != null, Question::getAcceptedAnswerId, expectCurrent));
    }

    @Override
    public List<QuestionSyncVO> syncQuestions(LocalDateTime since, Long lastId, Integer size) {
        int limit = (size == null || size <= 0) ? SYNC_DEFAULT_SIZE : Math.min(size, SYNC_MAX_SIZE);
        long cursor = lastId == null ? 0L : lastId;
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getUserId, Question::getTitle, Question::getContent,
                        Question::getAnswerCount, Question::getAcceptedAnswerId,
                        Question::getCreateTime, Question::getUpdateTime, Question::getDeleted);
        if (since == null) {
            wrapper.gt(Question::getId, cursor).orderByAsc(Question::getId);
        } else {
            wrapper.and(q -> q.gt(Question::getUpdateTime, since)
                            .or(o -> o.eq(Question::getUpdateTime, since).gt(Question::getId, cursor)))
                    .orderByAsc(Question::getUpdateTime)
                    .orderByAsc(Question::getId);
        }
        return questionMapper.selectPage(new Page<>(1, limit, false), wrapper).getRecords().stream()
                .map(question -> QuestionSyncVO.builder()
                        .questionId(question.getId())
                        .userId(question.getUserId())
                        .title(question.getTitle())
                        .content(question.getContent())
                        .answerCount(question.getAnswerCount())
                        .acceptedAnswerId(question.getAcceptedAnswerId())
                        .createTime(question.getCreateTime())
                        .updateTime(question.getUpdateTime())
                        .deleted(question.getDeleted() == null ? 0 : question.getDeleted().intValue())
                        .build())
                .toList();
    }

    private QuestionListItemVO toListItem(Question question) {
        return QuestionListItemVO.builder()
                .questionId(question.getId())
                .userId(question.getUserId())
                .title(question.getTitle())
                .answerCount(question.getAnswerCount())
                .solved(question.getAcceptedAnswerId() != null)
                .createTime(question.getCreateTime())
                .build();
    }

    private AnswerVO toAnswerVO(Answer answer) {
        return AnswerVO.builder()
                .answerId(answer.getId())
                .questionId(answer.getQuestionId())
                .userId(answer.getUserId())
                .content(answer.getContent())
                .createTime(answer.getCreateTime())
                .build();
    }

    @Override
    public List<Long> listAnswerIds(Long questionId) {
        return answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                        .select(Answer::getId)
                        .eq(Answer::getQuestionId, questionId))
                .stream()
                .map(Answer::getId)
                .toList();
    }
}
