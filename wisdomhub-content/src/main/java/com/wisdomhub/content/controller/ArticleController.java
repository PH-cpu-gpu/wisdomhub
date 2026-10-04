package com.wisdomhub.content.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章controller")
@Validated
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ArticleController {

}
