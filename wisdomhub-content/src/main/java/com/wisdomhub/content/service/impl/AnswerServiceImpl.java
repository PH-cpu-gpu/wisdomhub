package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.CommentConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.constants.QuestionRedisKeyConstants;
import com.wisdomhub.content.domain.dto.AnswerCreateDTO;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.AnswerLike;
import com.wisdomhub.content.domain.entity.Comment;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.domain.vo.AnswerVO;
import com.wisdomhub.content.mapper.AnswerLikeMapper;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.CommentMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import com.wisdomhub.content.service.AnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerServiceImpl implements AnswerService {

    private static final String LIKE_COUNT_COLUMN = "like_count";
    private static final String ANSWER_COUNT_COLUMN = "answer_count";

    private final AnswerMapper answerMapper;
    private final AnswerLikeMapper answerLikeMapper;
    private final CommentMapper commentMapper;
    private final QuestionMapper questionMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
        requireVisibleQuestion(questionId);
        Answer answer = Answer.builder()
                .questionId(questionId)
                .userId(userId)
                .content(dto.getContent())
                .likeCount(0)
                .commentCount(0)
                .deleted(QuestionConstants.NOT_DELETED.byteValue())
                .build();
        answerMapper.insert(answer);
        if (bumpAnswerCount(questionId, 1) == 0) {
            // 校验之后问题被并发删除：抛出去回滚这次插入
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "问题不存在");
        }
        return answer.getId();
    }

    @Override
    @Transactional
    public int like(Long answerId) {
        Long userId = CurrentUserUtil.id();
        Answer answer = requireVisibleAnswer(answerId);
        String gateKey = QuestionRedisKeyConstants.ANSWER_LIKE_ONCE_KEY + userId + ":" + answerId;
        if (!passGate(gateKey)) {
            // 窗口内已点过且没取消：直接返回，不碰 DB
            return answer.getLikeCount();
        }
        try {
            answerLikeMapper.insert(AnswerLike.builder()
                    .userId(userId)
                    .answerId(answerId)
                    .build());
        } catch (DuplicateKeyException e) {
            // Redis 丢过 key 但关系行还在：幂等返回 200，不能让它冒成 409
            return answer.getLikeCount();
        }
        if (bumpLikeCount(answerId, 1) == 0) {
            // 校验之后回答被并发删：回滚这次插入，并撤掉 gate，别把用户白拦一个窗口
            clearGate(gateKey);
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "回答不存在");
        }
        return readLikeCount(answerId);
    }

    @Override
    @Transactional
    public int unlike(Long answerId) {
        Long userId = CurrentUserUtil.id();
        Answer answer = requireVisibleAnswer(answerId);
        // 先撤 gate：不撤的话用户在这个窗口内没法再点回来
        clearGate(QuestionRedisKeyConstants.ANSWER_LIKE_ONCE_KEY + userId + ":" + answerId);
        int deleted = answerLikeMapper.delete(new LambdaQueryWrapper<AnswerLike>()
                .eq(AnswerLike::getUserId, userId)
                .eq(AnswerLike::getAnswerId, answerId));
        if (deleted == 0) {
            // 本来就没点过：幂等（17.3），不是错误
            return answer.getLikeCount();
        }
        bumpLikeCount(answerId, -1);
        return readLikeCount(answerId);
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

        // 点赞关系行没有软删语义，留着是永久孤儿 → 物理删
        answerLikeMapper.delete(new LambdaQueryWrapper<AnswerLike>()
                .eq(AnswerLike::getAnswerId, answerId));

        // 该回答下的评论一起软删
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .set(Comment::getDeleted, CommentConstants.DELETED.byteValue())
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getTargetType, CommentConstants.TARGET_TYPE_ANSWER.byteValue())
                .eq(Comment::getTargetId, answerId)
                .eq(Comment::getDeleted, CommentConstants.NOT_DELETED));
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

    /** 回答点赞数原子增减。不 bump answer.update_time（纯计数） */
    private int bumpLikeCount(Long answerId, int delta) {
        LambdaUpdateWrapper<Answer> wrapper = new LambdaUpdateWrapper<Answer>()
                .setSql(LIKE_COUNT_COLUMN + " = " + LIKE_COUNT_COLUMN + " + " + delta)
                .eq(Answer::getId, answerId)
                .eq(Answer::getDeleted, QuestionConstants.NOT_DELETED);
        if (delta < 0) {
            wrapper.ge(Answer::getLikeCount, 1);
        }
        return answerMapper.update(null, wrapper);
    }

    /** 成功路径读回最新点赞数。列是 NOT NULL DEFAULT 0，判空只防「事务内被并发硬删」 */
    private int readLikeCount(Long answerId) {
        Answer latest = answerMapper.selectById(answerId);
        if (latest == null || latest.getLikeCount() == null) {
            return 0;
        }
        return latest.getLikeCount();
    }

    /** 防重前置拦截：SETNX 成功 = 窗口内首次。Redis 不可用时一律放行（同 B1.26） */
    private boolean passGate(String gateKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(gateKey, "1", RedisTtl.LIKE_ONCE));
        } catch (Exception e) {
            log.warn("防重前置拦截不可用, 放行由唯一索引兜底: key={}", gateKey, e);
            return true;
        }
    }

    private void clearGate(String gateKey) {
        try {
            stringRedisTemplate.delete(gateKey);
        } catch (Exception e) {
            log.warn("防重 gate 清除失败, 等 TTL 自愈: key={}", gateKey, e);
        }
    }

    /** 与 QuestionServiceImpl.toAnswerVO 同形 */
    private AnswerVO toAnswerVO(Answer answer) {
        return AnswerVO.builder()
                .answerId(answer.getId())
                .questionId(answer.getQuestionId())
                .userId(answer.getUserId())
                .content(answer.getContent())
                .likeCount(answer.getLikeCount())
                .commentCount(answer.getCommentCount())
                .createTime(answer.getCreateTime())
                .build();
    }
}
