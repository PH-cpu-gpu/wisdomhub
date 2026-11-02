package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.TagProposalSubmittedEvent;
import com.wisdomhub.common.mq.event.payload.TagProposalSubmittedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.TagConstants;
import com.wisdomhub.content.constants.TagProposalConstants;
import com.wisdomhub.content.constants.TagProposalIssueCodes;
import com.wisdomhub.content.constants.TagRelationConstants;
import com.wisdomhub.content.domain.dto.TagDTO;
import com.wisdomhub.content.domain.dto.TagProposalSubmitDTO;
import com.wisdomhub.content.domain.dto.TagRelationDTO;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.entity.TagAlias;
import com.wisdomhub.content.domain.entity.TagProposal;
import com.wisdomhub.content.domain.entity.TagRelation;
import com.wisdomhub.content.domain.vo.TagProposalIssueVO;
import com.wisdomhub.content.domain.vo.TagProposalSubmitResult;
import com.wisdomhub.content.domain.vo.TagProposalVO;
import com.wisdomhub.content.domain.vo.TagProposalValidateVO;
import com.wisdomhub.content.mapper.TagAliasMapper;
import com.wisdomhub.content.mapper.TagMapper;
import com.wisdomhub.content.mapper.TagProposalMapper;
import com.wisdomhub.content.mapper.TagRelationMapper;
import com.wisdomhub.content.service.TagProposalService;
import com.wisdomhub.content.service.TagRelationService;
import com.wisdomhub.content.service.TagService;
import com.wisdomhub.content.util.TagNameNormalizer;
import com.wisdomhub.content.util.TagRelationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagProposalServiceImpl implements TagProposalService {

    private static final int TARGET_KEY_MAX_LENGTH = 191;

    private final TagProposalMapper tagProposalMapper;
    private final TagMapper tagMapper;
    private final TagAliasMapper tagAliasMapper;
    private final TagRelationMapper tagRelationMapper;
    private final TagService tagService;
    private final TagRelationService tagRelationService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public TagProposalSubmitResult submit(TagProposalSubmitDTO dto) {
        TagProposalValidateVO validation = validate(dto);
        if (!validation.isValid()) {
            return TagProposalSubmitResult.builder()
                    .valid(false)
                    .issues(validation.getIssues())
                    .build();
        }
        TagProposalVO proposal = validation.getProposalType() == TagProposalConstants.TYPE_TAG
                ? insertTagProposal(dto, validation.getTargetKey())
                : insertRelationProposal(dto, validation.getTargetKey());
        return TagProposalSubmitResult.builder()
                .valid(true)
                .issues(List.of())
                .proposal(proposal)
                .build();
    }

    @Override
    public TagProposalValidateVO validate(TagProposalSubmitDTO dto) {
        List<TagProposalIssueVO> issues = new ArrayList<>();
        Integer proposalType = dto.getProposalType();
        String targetKey = null;
        if (proposalType == null) {
            issues.add(issue("proposalType", TagProposalIssueCodes.PROPOSAL_TYPE_INVALID, "提案类型不能为空"));
        } else if (proposalType == TagProposalConstants.TYPE_TAG) {
            targetKey = validateTagProposal(dto, issues);
        } else if (proposalType == TagProposalConstants.TYPE_RELATION) {
            targetKey = validateRelationProposal(dto, issues);
        } else {
            issues.add(issue("proposalType", TagProposalIssueCodes.PROPOSAL_TYPE_INVALID, "提案类型不合法"));
        }
        validateProposerType(dto.getProposerType(), issues);
        return TagProposalValidateVO.builder()
                .valid(issues.isEmpty())
                .proposalType(proposalType)
                .targetKey(targetKey)
                .issues(issues)
                .build();
    }

    @Override
    public PageResponse<TagProposalVO> page(Integer status, Integer proposalType, Integer page, Integer pageSize) {
        if (status != null && (status < TagProposalConstants.STATUS_PENDING
                || status > TagProposalConstants.STATUS_EXPIRED)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案状态不合法");
        }
        if (proposalType != null && proposalType != TagProposalConstants.TYPE_TAG
                && proposalType != TagProposalConstants.TYPE_RELATION) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案类型不合法");
        }
        Page<TagProposal> result = tagProposalMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<TagProposal>()
                        .eq(status != null, TagProposal::getStatus, status)
                        .eq(proposalType != null, TagProposal::getProposalType, proposalType)
                        .orderByDesc(TagProposal::getCreateTime)
                        .orderByDesc(TagProposal::getId));
        return PageUtil.toResponse(result, result.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public TagProposalVO detail(Long id) {
        TagProposal proposal = tagProposalMapper.selectById(id);
        if (proposal == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "提案不存在");
        }
        return toVO(proposal);
    }

    @Override
    @Transactional
    public void approve(Long id) {
        TagProposal proposal = requirePending(id);
        if (!casPending(id, TagProposalConstants.STATUS_APPROVED)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "提案已被其他管理员处理");
        }
        apply(proposal);
    }

    @Override
    public void reject(Long id) {
        requirePending(id);
        if (!casPending(id, TagProposalConstants.STATUS_REJECTED)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "提案已被其他管理员处理");
        }
    }

    private TagProposal requirePending(Long id) {
        TagProposal proposal = tagProposalMapper.selectById(id);
        if (proposal == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "提案不存在");
        }
        if (!Integer.valueOf(TagProposalConstants.STATUS_PENDING).equals(proposal.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "提案已处理");
        }
        return proposal;
    }

    private boolean casPending(Long id, int targetStatus) {
        Long adminId = CurrentUserUtil.id();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // CAS：只有仍是待审的那一个管理员能成功，另一个 affected=0
        return tagProposalMapper.update(null, new LambdaUpdateWrapper<TagProposal>()
                .set(TagProposal::getStatus, targetStatus)
                .set(TagProposal::getDecidedBy, adminId)
                .set(TagProposal::getDecidedAt, now)
                .set(TagProposal::getUpdateTime, now)
                .eq(TagProposal::getId, id)
                .eq(TagProposal::getStatus, TagProposalConstants.STATUS_PENDING)) > 0;
    }

    /** 状态 CAS 之后执行；抛异常会连同状态一起回滚，提案保持待审 */
    private void apply(TagProposal proposal) {
        JsonNode payload = readJson(proposal.getPayload());
        if (payload == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案内容不合法");
        }
        if (Integer.valueOf(TagProposalConstants.TYPE_TAG).equals(proposal.getProposalType())) {
            TagDTO dto = new TagDTO();
            dto.setName(text(payload, "name"));
            dto.setCategory(text(payload, "category"));
            dto.setDescription(text(payload, "description"));
            tagService.createTag(dto);
            return;
        }
        if (Integer.valueOf(TagProposalConstants.TYPE_RELATION).equals(proposal.getProposalType())) {
            TagRelationDTO dto = new TagRelationDTO();
            dto.setFromTagId(longValue(payload, "fromTagId"));
            dto.setToTagId(longValue(payload, "toTagId"));
            dto.setRelationType(text(payload, "relationType"));
            int source = Integer.valueOf(TagProposalConstants.PROPOSER_ADMIN).equals(proposal.getProposerType())
                    ? TagRelationConstants.SOURCE_ADMIN
                    : TagRelationConstants.SOURCE_AI;
            tagRelationService.createFromProposal(dto, source, proposal.getId());
            return;
        }
        throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案类型不合法");
    }

    @Override
    public int expirePending() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cutoff = now.minusDays(TagProposalConstants.EXPIRE_DAYS);
        return tagProposalMapper.update(null, new LambdaUpdateWrapper<TagProposal>()
                .set(TagProposal::getStatus, TagProposalConstants.STATUS_EXPIRED)
                .set(TagProposal::getUpdateTime, now)
                .eq(TagProposal::getStatus, TagProposalConstants.STATUS_PENDING)
                .lt(TagProposal::getCreateTime, cutoff));
    }

    private String validateTagProposal(TagProposalSubmitDTO dto, List<TagProposalIssueVO> issues) {
        String name = StrUtil.trim(dto.getName());
        if (StrUtil.isEmpty(name)) {
            issues.add(issue("name", TagProposalIssueCodes.NAME_REQUIRED, "标签名不能为空"));
            return null;
        }
        if (name.length() > TagNameNormalizer.MAX_LENGTH) {
            issues.add(issue("name", TagProposalIssueCodes.NAME_TOO_LONG, "标签名长度不能超过50"));
        }
        String nameNorm = TagNameNormalizer.normalize(name);
        if (nameNorm.isEmpty()) {
            issues.add(issue("name", TagProposalIssueCodes.NAME_REQUIRED, "标签名不能为空"));
            return null;
        }
        if (nameNorm.length() > TagNameNormalizer.MAX_LENGTH) {
            issues.add(issue("name", TagProposalIssueCodes.NAME_TOO_LONG, "标签名归一化后不能超过50字"));
            return null;
        }
        String category = StrUtil.blankToDefault(dto.getCategory(), TagConstants.CATEGORY_DEFAULT).trim();
        if (category.length() > 20) {
            issues.add(issue("category", TagProposalIssueCodes.CATEGORY_TOO_LONG, "分组长度不能超过20"));
        }
        String description = StrUtil.nullToEmpty(dto.getDescription()).trim();
        if (description.length() > 255) {
            issues.add(issue("description", TagProposalIssueCodes.DESCRIPTION_TOO_LONG, "描述长度不能超过255"));
        }
        boolean exists = tagMapper.exists(new LambdaQueryWrapper<Tag>().eq(Tag::getNameNorm, nameNorm))
                || tagAliasMapper.exists(new LambdaQueryWrapper<TagAlias>().eq(TagAlias::getAliasNorm, nameNorm));
        if (exists) {
            issues.add(issue("name", TagProposalIssueCodes.TAG_EXISTS, "标签或别名已存在"));
        }
        if (pendingExists(nameNorm)) {
            issues.add(issue("targetKey", TagProposalIssueCodes.PENDING_EXISTS, "该目标已有待审提案"));
        }
        return nameNorm;
    }

    private String validateRelationProposal(TagProposalSubmitDTO dto, List<TagProposalIssueVO> issues) {
        Long fromTagId = dto.getFromTagId();
        Long toTagId = dto.getToTagId();
        if (fromTagId == null || toTagId == null) {
            issues.add(issue("fromTagId", TagProposalIssueCodes.RELATION_ENDPOINT_REQUIRED, "关系两端不能为空"));
            return null;
        }
        if (fromTagId.equals(toTagId)) {
            issues.add(issue("toTagId", TagProposalIssueCodes.RELATION_SELF_LOOP, "关系不能指向自己"));
            return null;
        }
        String relationType = StrUtil.trim(dto.getRelationType()).toUpperCase(Locale.ROOT);
        if (!TagRelationConstants.TYPES.contains(relationType)) {
            issues.add(issue("relationType", TagProposalIssueCodes.RELATION_TYPE_INVALID, "关系类型不合法"));
            return null;
        }
        String targetKey = relationTargetKey(fromTagId, toTagId, relationType);

        Long found = tagMapper.selectCount(new LambdaQueryWrapper<Tag>().in(Tag::getId, List.of(fromTagId, toTagId)));
        if (found == null || found != 2) {
            issues.add(issue("fromTagId", TagProposalIssueCodes.TAG_NOT_FOUND, "标签不存在"));
        } else {
            boolean exists = relationExists(fromTagId, toTagId, relationType)
                    || (TagRelationConstants.SIMILAR_TO.equals(relationType)
                    && relationExists(toTagId, fromTagId, relationType));
            if (exists) {
                issues.add(issue("relationType", TagProposalIssueCodes.RELATION_EXISTS, "关系已存在"));
            } else if (TagRelationConstants.ACYCLIC_TYPES.contains(relationType)) {
                List<TagRelation> sameType = tagRelationMapper.selectList(
                        new LambdaQueryWrapper<TagRelation>().eq(TagRelation::getRelationType, relationType));
                if (TagRelationValidator.createsCycle(sameType, fromTagId, toTagId)) {
                    issues.add(issue("relationType", TagProposalIssueCodes.RELATION_CYCLE, "该关系会形成环"));
                }
            }
        }
        if (pendingExists(targetKey)) {
            issues.add(issue("targetKey", TagProposalIssueCodes.PENDING_EXISTS, "该目标已有待审提案"));
        }
        return targetKey;
    }

    private void validateProposerType(Integer proposerType, List<TagProposalIssueVO> issues) {
        if (proposerType != null && (proposerType < TagProposalConstants.PROPOSER_AI
                || proposerType > TagProposalConstants.PROPOSER_SYSTEM)) {
            issues.add(issue("proposerType", TagProposalIssueCodes.PROPOSER_TYPE_INVALID, "提案人类型不合法"));
        }
    }

    private TagProposalVO insertTagProposal(TagProposalSubmitDTO dto, String targetKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", StrUtil.trim(dto.getName()));
        payload.put("category", StrUtil.blankToDefault(dto.getCategory(), TagConstants.CATEGORY_DEFAULT).trim());
        payload.put("description", StrUtil.nullToEmpty(dto.getDescription()).trim());
        return insertProposal(TagProposalConstants.TYPE_TAG, targetKey, payload, dto);
    }

    private TagProposalVO insertRelationProposal(TagProposalSubmitDTO dto, String targetKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromTagId", dto.getFromTagId());
        payload.put("toTagId", dto.getToTagId());
        payload.put("relationType", StrUtil.trim(dto.getRelationType()).toUpperCase(Locale.ROOT));
        return insertProposal(TagProposalConstants.TYPE_RELATION, targetKey, payload, dto);
    }

    private TagProposalVO insertProposal(int proposalType, String targetKey,
                                         Map<String, Object> payload, TagProposalSubmitDTO dto) {
        if (targetKey == null || targetKey.length() > TARGET_KEY_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案目标不合法");
        }
        TagProposal proposal = TagProposal.builder()
                .proposalType(proposalType)
                .status(TagProposalConstants.STATUS_PENDING)
                .targetKey(targetKey)
                .payload(writeJson(payload))
                .evidence(dto.getEvidence() == null ? null : writeJson(dto.getEvidence()))
                .proposerType(normalizeProposerType(dto.getProposerType()))
                .proposerId(dto.getProposerId())
                .build();
        try {
            tagProposalMapper.insert(proposal);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该目标已有待审提案");
        }
        sendEvent(new TagProposalSubmittedEvent(TagProposalSubmittedPayload.builder()
                .proposalId(proposal.getId())
                .proposalType(proposalType)
                .targetKey(targetKey)
                .proposerType(proposal.getProposerType())
                .build()));
        return toVO(proposal);
    }

    private void sendEvent(BaseEvent<?> event) {
        if (event.getPayload() == null) {
            return;
        }
        try {
            eventPublisher.send(event);
        } catch (Exception e) {
            log.error("提案事件发送失败, eventType={}", event.getEventType(), e);
        }
    }

    private boolean pendingExists(String targetKey) {
        return tagProposalMapper.exists(new LambdaQueryWrapper<TagProposal>()
                .eq(TagProposal::getStatus, TagProposalConstants.STATUS_PENDING)
                .eq(TagProposal::getTargetKey, targetKey));
    }

    private TagProposalVO toVO(TagProposal proposal) {
        TagProposalVO.TagProposalVOBuilder builder = TagProposalVO.builder()
                .id(proposal.getId())
                .proposalType(proposal.getProposalType())
                .status(proposal.getStatus())
                .targetKey(proposal.getTargetKey())
                .proposerType(proposal.getProposerType())
                .proposerId(proposal.getProposerId())
                .decidedBy(proposal.getDecidedBy())
                .decidedAt(proposal.getDecidedAt())
                .createTime(proposal.getCreateTime())
                .evidence(readJson(proposal.getEvidence()));
        JsonNode payload = readJson(proposal.getPayload());
        if (payload != null) {
            if (Integer.valueOf(TagProposalConstants.TYPE_TAG).equals(proposal.getProposalType())) {
                builder.name(text(payload, "name"))
                        .category(text(payload, "category"))
                        .description(text(payload, "description"));
            } else if (Integer.valueOf(TagProposalConstants.TYPE_RELATION).equals(proposal.getProposalType())) {
                builder.fromTagId(longValue(payload, "fromTagId"))
                        .toTagId(longValue(payload, "toTagId"))
                        .relationType(text(payload, "relationType"));
            }
        }
        return builder.build();
    }

    private boolean relationExists(Long fromTagId, Long toTagId, String relationType) {
        return tagRelationMapper.exists(new LambdaQueryWrapper<TagRelation>()
                .eq(TagRelation::getFromTagId, fromTagId)
                .eq(TagRelation::getToTagId, toTagId)
                .eq(TagRelation::getRelationType, relationType));
    }

    private String relationTargetKey(Long fromTagId, Long toTagId, String relationType) {
        if (TagRelationConstants.SIMILAR_TO.equals(relationType)) {
            long first = Math.min(fromTagId, toTagId);
            long second = Math.max(fromTagId, toTagId);
            return first + ":" + relationType + ":" + second;
        }
        return fromTagId + ":" + relationType + ":" + toTagId;
    }

    private int normalizeProposerType(Integer proposerType) {
        if (proposerType == null) {
            return TagProposalConstants.PROPOSER_AI;
        }
        if (proposerType < TagProposalConstants.PROPOSER_AI || proposerType > TagProposalConstants.PROPOSER_SYSTEM) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "提案人类型不合法");
        }
        return proposerType;
    }

    private TagProposalIssueVO issue(String field, String code, String message) {
        return TagProposalIssueVO.builder()
                .field(field)
                .code(code)
                .message(message)
                .severity(TagProposalIssueCodes.SEVERITY_ERROR)
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化提案内容失败", e);
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("提案 JSON 解析失败", e);
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
