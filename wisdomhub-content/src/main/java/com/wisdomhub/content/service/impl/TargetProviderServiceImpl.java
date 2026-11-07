package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import com.wisdomhub.content.service.TargetProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TargetProviderServiceImpl implements TargetProviderService {

    private final ArticleMapper articleMapper;
    private final AnswerMapper answerMapper;
    private final QuestionMapper questionMapper;

    @Override
    public TargetInfoDTO getTarget(String type, Long id) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetType 不能为空");
        }
        int code;
        try {
            code = ContentTargetTypeConstants.codeOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetType 不合法");
        }
        return switch (code) {
            case ContentTargetTypeConstants.ARTICLE -> article(id);
            case ContentTargetTypeConstants.ANSWER -> answer(id);
            case ContentTargetTypeConstants.QUESTION -> question(id);
            default -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "content-service 不支持该 targetType");
        };
    }

    private TargetInfoDTO article(Long id) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getAuthorId, Article::getState, Article::getDeleted)
                .eq(Article::getId, id));
        if (article == null || article.getDeleted() == ArticleConstants.DELETED.byteValue()) {
            return missing();
        }
        return found(article.getState() == ArticleConstants.PUBLISH.byteValue(), article.getAuthorId());
    }

    private TargetInfoDTO answer(Long id) {
        Answer answer = answerMapper.selectOne(new LambdaQueryWrapper<Answer>()
                .select(Answer::getId, Answer::getUserId, Answer::getDeleted)
                .eq(Answer::getId, id));
        // 问题删除会级联软删回答，所以只看 answer.deleted
        if (answer == null || answer.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            return missing();
        }
        return found(true, answer.getUserId());
    }

    private TargetInfoDTO question(Long id) {
        Question question = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getUserId, Question::getDeleted)
                .eq(Question::getId, id));
        if (question == null || question.getDeleted() == QuestionConstants.DELETED.byteValue()) {
            return missing();
        }
        return found(true, question.getUserId());
    }

    private TargetInfoDTO missing() {
        TargetInfoDTO dto = new TargetInfoDTO();
        dto.setExists(false);
        dto.setVisible(false);
        return dto;
    }

    private TargetInfoDTO found(boolean visible, Long ownerId) {
        TargetInfoDTO dto = new TargetInfoDTO();
        dto.setExists(true);
        dto.setVisible(visible);
        dto.setOwnerId(ownerId);
        return dto;
    }
}
