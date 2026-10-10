package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.AnswerCreateDTO;
import com.wisdomhub.content.domain.dto.QuestionCreateDTO;
import com.wisdomhub.content.domain.vo.AnswerVO;
import com.wisdomhub.content.domain.vo.QuestionDetailVO;
import com.wisdomhub.content.domain.vo.QuestionListItemVO;
import com.wisdomhub.content.service.AnswerService;
import com.wisdomhub.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "问答controller")
@Validated
@RestController
@RequestMapping("/api/content/question")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final AnswerService answerService;

    @Operation(summary = "提问")
    @PostMapping
    public R<Long> create(@Valid @RequestBody QuestionCreateDTO questionCreateDTO) {
        return R.success(questionService.createQuestion(questionCreateDTO));
    }

    @Operation(summary = "问题列表（sort=new|hot）")
    @GetMapping
    public R<PageResponse<QuestionListItemVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(defaultValue = "new") String sort) {
        return R.success(questionService.listQuestions(page, pageSize, sort));
    }

    @Operation(summary = "我的问题收藏")
    @GetMapping("/favorite")
    public R<PageResponse<QuestionListItemVO>> myFavorites(@RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(questionService.listMyFavoriteQuestions(page, pageSize));
    }

    @Operation(summary = "问题详情")
    @GetMapping("/{questionId}")
    public R<QuestionDetailVO> detail(@PathVariable Long questionId) {
        return R.success(questionService.getQuestionDetail(questionId));
    }

    @Operation(summary = "删除问题")
    @DeleteMapping("/{questionId}")
    public R<Void> delete(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return R.success();
    }

    @Operation(summary = "收藏问题")
    @PutMapping("/{questionId}/favorite")
    public R<Integer> favorite(@PathVariable Long questionId) {
        return R.success(questionService.favorite(questionId));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{questionId}/favorite")
    public R<Integer> unfavorite(@PathVariable Long questionId) {
        return R.success(questionService.unfavorite(questionId));
    }

    @Operation(summary = "采纳答案")
    @PutMapping("/{questionId}/accept/{answerId}")
    public R<Void> accept(@PathVariable Long questionId, @PathVariable Long answerId) {
        questionService.acceptAnswer(questionId, answerId);
        return R.success();
    }

    @Operation(summary = "取消采纳")
    @DeleteMapping("/{questionId}/accept/{answerId}")
    public R<Void> cancelAccept(@PathVariable Long questionId, @PathVariable Long answerId) {
        questionService.cancelAccept(questionId, answerId);
        return R.success();
    }

    @Operation(summary = "回答问题")
    @PostMapping("/{questionId}/answer")
    public R<Long> createAnswer(@PathVariable Long questionId,
                                @Valid @RequestBody AnswerCreateDTO answerCreateDTO) {
        return R.success(answerService.createAnswer(questionId, answerCreateDTO));
    }

    @Operation(summary = "回答分页")
    @GetMapping("/{questionId}/answer")
    public R<PageResponse<AnswerVO>> answers(@PathVariable Long questionId,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(answerService.listAnswers(questionId, page, pageSize));
    }
}
