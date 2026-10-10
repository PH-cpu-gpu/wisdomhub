package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.CommentConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.constants.QuestionRedisKeyConstants;
import com.wisdomhub.content.domain.dto.QuestionCreateDTO;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.AnswerLike;
import com.wisdomhub.content.domain.entity.Comment;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.domain.entity.QuestionFavorite;
import com.wisdomhub.content.domain.vo.AnswerVO;
import com.wisdomhub.content.domain.vo.QuestionDetailVO;
import com.wisdomhub.content.domain.vo.QuestionListItemVO;
import com.wisdomhub.content.mapper.AnswerLikeMapper;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.CommentMapper;
import com.wisdomhub.content.mapper.QuestionFavoriteMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import com.wisdomhub.content.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private static final String FAVORITE_COUNT_COLUMN = "favorite_count";

    private final QuestionMapper questionMapper;
    private final QuestionFavoriteMapper questionFavoriteMapper;
    private final AnswerMapper answerMapper;
    private final AnswerLikeMapper answerLikeMapper;
    private final CommentMapper commentMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 读 ====================

    @Override
    public PageResponse<QuestionListItemVO> listQuestions(Integer page, Integer pageSize, String sort) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getUserId, Question::getTitle, Question::getAnswerCount,
                        Question::getFavoriteCount, Question::getAcceptedAnswerId, Question::getCreateTime)
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
                .favoriteCount(question.getFavoriteCount())
                .acceptedAnswerId(question.getAcceptedAnswerId())
                .acceptedAnswer(accepted == null ? null : toAnswerVO(accepted))
                .favorited(resolveFavorited(questionId))
                .createTime(question.getCreateTime())
                .build();
    }

    @Override
    public PageResponse<QuestionListItemVO> listMyFavoriteQuestions(Integer page, Integer pageSize) {
        IPage<Question> result = questionMapper.selectFavoritePage(PageUtil.of(page, pageSize),
                CurrentUserUtil.id(), QuestionConstants.NOT_DELETED);
        return PageUtil.toResponse(result, result.getRecords().stream().map(this::toListItem).toList());
    }

    // ==================== 写 ====================

    @Override
    public Long createQuestion(QuestionCreateDTO dto) {
        Question question = Question.builder()
                .userId(CurrentUserUtil.id())
                .title(dto.getTitle())
                .content(dto.getContent())
                .answerCount(0)
                .favoriteCount(0)
                .deleted(QuestionConstants.NOT_DELETED.byteValue())
                .build();
        questionMapper.insert(question);
        return question.getId();
    }

    @Override
    @Transactional
    public int favorite(Long questionId) {
        Long userId = CurrentUserUtil.id();
        Question question = requireVisibleQuestion(questionId);
        String gateKey = QuestionRedisKeyConstants.QUESTION_FAVORITE_ONCE_KEY + userId + ":" + questionId;
        if (!passGate(gateKey)) {
            // 窗口内已收藏且没取消：直接返回，不碰 DB
            return question.getFavoriteCount();
        }
        try {
            questionFavoriteMapper.insert(QuestionFavorite.builder()
                    .userId(userId)
                    .questionId(questionId)
                    .build());
        } catch (DuplicateKeyException e) {
            // Redis 丢过 key 但关系行还在：幂等返回 200，不能让它冒成 409
            return question.getFavoriteCount();
        }
        if (bumpFavoriteCount(questionId, 1) == 0) {
            // 校验之后问题被并发删除：回滚这次插入，并撤掉 gate，别把用户白拦一个窗口
            clearGate(gateKey);
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "问题不存在");
        }
        return readFavoriteCount(questionId);
    }

    @Override
    @Transactional
    public int unfavorite(Long questionId) {
        Long userId = CurrentUserUtil.id();
        Question question = requireVisibleQuestion(questionId);
        // 先撤 gate：不撤的话用户在这个窗口内没法再收藏回来
        clearGate(QuestionRedisKeyConstants.QUESTION_FAVORITE_ONCE_KEY + userId + ":" + questionId);
        int deleted = questionFavoriteMapper.delete(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId));
        if (deleted == 0) {
            // 本来就没收藏过：幂等，不是错误
            return question.getFavoriteCount();
        }
        bumpFavoriteCount(questionId, -1);
        return readFavoriteCount(questionId);
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

        // 这些回答的评论一起软删 —— 与 deleteAnswer 同一条语句，这里把 target 从一个回答换成一整批。
        // 子查询读的是刚软删过的 answer 行（还在表里），照样能捞到它们的 id
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .set(Comment::getDeleted, CommentConstants.DELETED.byteValue())
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getTargetType, CommentConstants.TARGET_TYPE_ANSWER.byteValue())
                .inSql(Comment::getTargetId, "SELECT id FROM answer WHERE question_id = " + questionId)
                .eq(Comment::getDeleted, CommentConstants.NOT_DELETED));

        answerLikeMapper.delete(new LambdaQueryWrapper<AnswerLike>()
                .inSql(AnswerLike::getAnswerId, "SELECT id FROM answer WHERE question_id = " + questionId));
        questionFavoriteMapper.delete(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getQuestionId, questionId));
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

    /** 防重前置拦截：SETNX 成功 = 窗口内首次，失败 = 窗口内已收藏。Redis 不可用时一律放行 */
    private boolean passGate(String gateKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(gateKey, "1", RedisTtl.LIKE_ONCE));
        } catch (Exception e) {
            log.warn("防重前置拦截不可用, 放行由唯一索引兜底: key={}", gateKey, e);
            return true;
        }
    }

    /** 撤掉 gate：取消操作，或本次操作实际没生效。清不掉只记日志，TTL 会自愈 */
    private void clearGate(String gateKey) {
        try {
            stringRedisTemplate.delete(gateKey);
        } catch (Exception e) {
            log.warn("防重 gate 清除失败, 等 TTL 自愈: key={}", gateKey, e);
        }
    }

    /**
     * 收藏数原子增减，绝不读改写。不 bump update_time（同 B1.26：纯计数不该把整行写一遍）。
     * delta 为负时带非负安全网
     */
    private int bumpFavoriteCount(Long questionId, int delta) {
        LambdaUpdateWrapper<Question> wrapper = new LambdaUpdateWrapper<Question>()
                .setSql(FAVORITE_COUNT_COLUMN + " = " + FAVORITE_COUNT_COLUMN + " + " + delta)
                .eq(Question::getId, questionId)
                .eq(Question::getDeleted, QuestionConstants.NOT_DELETED);
        if (delta < 0) {
            wrapper.ge(Question::getFavoriteCount, 1);
        }
        return questionMapper.update(null, wrapper);
    }

    /** 成功路径读回最新收藏数。列是 NOT NULL DEFAULT 0，判空只防「事务内被并发硬删」 */
    private int readFavoriteCount(Long questionId) {
        Question latest = questionMapper.selectById(questionId);
        if (latest == null || latest.getFavoriteCount() == null) {
            return 0;
        }
        return latest.getFavoriteCount();
    }

    private boolean resolveFavorited(Long questionId) {
        Long userId = UserContext.getUserId();
        return userId != null && questionFavoriteMapper.exists(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId));
    }

    private QuestionListItemVO toListItem(Question question) {
        return QuestionListItemVO.builder()
                .questionId(question.getId())
                .userId(question.getUserId())
                .title(question.getTitle())
                .answerCount(question.getAnswerCount())
                .favoriteCount(question.getFavoriteCount())
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
                .likeCount(answer.getLikeCount())
                .commentCount(answer.getCommentCount())
                .createTime(answer.getCreateTime())
                .build();
    }
}
