package com.wisdomhub.interaction.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** 问题下所有回答 ID（含已删除），删除问题时清理回答级互动数据用。 */
@FeignClient(name = "content-service", contextId = "interactionQuestionAnswer", path = "/internal/content")
public interface QuestionAnswerFeign {

    @GetMapping("/question/{id}/answer/ids")
    List<Long> listAnswerIds(@PathVariable("id") Long questionId);
}
