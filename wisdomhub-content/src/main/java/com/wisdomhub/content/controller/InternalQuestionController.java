package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.vo.QuestionSyncVO;
import com.wisdomhub.content.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "服务间问题controller")
@Validated
@RestController
@RequestMapping("/internal/content")
@RequiredArgsConstructor
public class InternalQuestionController {

    private final QuestionService questionService;

    @Operation(summary = "按 (updateTime, id) 游标对账分页，供 search 修 question_index；包含已删行")
    @GetMapping("/question/sync")
    public R<List<QuestionSyncVO>> sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") Long lastId,
            @RequestParam(required = false) Integer size) {
        return R.success(questionService.syncQuestions(since, lastId, size));
    }

    @Operation(summary = "问题下所有回答 ID（含已删除），供 interaction 删除级联")
    @GetMapping("/question/{id}/answer/ids")
    public R<List<Long>> answerIds(@PathVariable("id") Long questionId) {
        return R.success(questionService.listAnswerIds(questionId));
    }
}
