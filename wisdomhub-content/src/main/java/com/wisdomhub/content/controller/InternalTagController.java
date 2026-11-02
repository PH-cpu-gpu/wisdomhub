package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.TagProposalSubmitDTO;
import com.wisdomhub.content.domain.vo.TagProposalIssueVO;
import com.wisdomhub.content.domain.vo.TagProposalSubmitResult;
import com.wisdomhub.content.domain.vo.TagRelationSyncVO;
import com.wisdomhub.content.domain.vo.TagResolveVO;
import com.wisdomhub.content.domain.vo.TagSyncVO;
import com.wisdomhub.content.domain.vo.TagVO;
import com.wisdomhub.content.service.TagProposalService;
import com.wisdomhub.content.service.TagRelationService;
import com.wisdomhub.content.service.TagService;
import com.wisdomhub.content.util.TagProposalErrorMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "服务间标签controller")
@Validated
@RestController
@RequestMapping("/internal/content")
@RequiredArgsConstructor
public class InternalTagController {

    private final TagService tagService;
    private final TagRelationService tagRelationService;
    private final TagProposalService tagProposalService;

    @Operation(summary = "全量标签字典，供 knowledge-service 建图谱种子")
    @GetMapping("/tag/all")
    public R<List<TagVO>> listAllTags() {
        return R.success(tagService.listAllTags());
    }

    @Operation(summary = "按 (updateTime, id) 游标对账分页，供 search 修 tag_index")
    @GetMapping("/tag/sync")
    public R<List<TagSyncVO>> sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") Long lastId,
            @RequestParam(required = false) Integer size) {
        return R.success(tagService.syncTags(since, lastId, size));
    }

    @Operation(summary = "按名字批量解析标签（归一化 + 别名），供 knowledge 解析抽取实体")
    @PostMapping("/tag/resolve")
    public R<List<TagResolveVO>> resolve(@RequestBody List<String> names) {
        return R.success(tagService.resolveNames(names));
    }

    @Operation(summary = "全量标签关系，供 knowledge 建图与对账")
    @GetMapping("/tag-relation/all")
    public R<List<TagRelationSyncVO>> listAllRelations() {
        return R.success(tagRelationService.listAll());
    }

    @Operation(summary = "提交标签/关系提案；校验失败时 data 里带 issues，供 Agent 调用")
    @PostMapping("/tag-proposal")
    public R<TagProposalSubmitResult> submitProposal(@RequestBody TagProposalSubmitDTO dto) {
        TagProposalSubmitResult result = tagProposalService.submit(dto);
        if (result.isValid()) {
            return R.success(result);
        }
        if (result.getIssues() == null || result.getIssues().isEmpty()) {
            return R.error(CommonErrorCode.BAD_REQUEST, "提案不合法", result);
        }
        TagProposalIssueVO first = result.getIssues().get(0);
        return R.error(TagProposalErrorMapper.toErrorCode(first.getCode()), first.getMessage(), result);
    }
}
