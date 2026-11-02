package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.TagRelationChangedEvent;
import com.wisdomhub.common.mq.event.payload.TagRelationChangedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.content.constants.TagRelationConstants;
import com.wisdomhub.content.domain.dto.TagRelationDTO;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.entity.TagRelation;
import com.wisdomhub.content.domain.vo.TagRelationSyncVO;
import com.wisdomhub.content.domain.vo.TagRelationVO;
import com.wisdomhub.content.mapper.TagMapper;
import com.wisdomhub.content.mapper.TagRelationMapper;
import com.wisdomhub.content.service.TagRelationService;
import com.wisdomhub.content.util.TagRelationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagRelationServiceImpl implements TagRelationService {

    private final TagMapper tagMapper;
    private final TagRelationMapper tagRelationMapper;
    private final EventPublisher eventPublisher;

    @Override
    public List<TagRelationVO> listByTag(Long tagId) {
        requireTag(tagId);
        List<TagRelation> relations = tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelation>()
                .and(wrapper -> wrapper.eq(TagRelation::getFromTagId, tagId)
                        .or()
                        .eq(TagRelation::getToTagId, tagId))
                .orderByAsc(TagRelation::getRelationType)
                .orderByAsc(TagRelation::getFromTagId)
                .orderByAsc(TagRelation::getToTagId));
        return toVOs(relations);
    }

    @Override
    public List<TagRelationSyncVO> listAll() {
        return tagRelationMapper.selectList(new LambdaQueryWrapper<TagRelation>()
                        .orderByAsc(TagRelation::getId))
                .stream()
                .map(relation -> TagRelationSyncVO.builder()
                        .relationId(String.valueOf(relation.getId()))
                        .fromTagId(String.valueOf(relation.getFromTagId()))
                        .toTagId(String.valueOf(relation.getToTagId()))
                        .relationType(relation.getRelationType())
                        .source(relation.getSource())
                        .proposalId(relation.getProposalId())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public TagRelationVO create(TagRelationDTO dto) {
        return doCreate(dto, TagRelationConstants.SOURCE_ADMIN, null);
    }

    @Override
    @Transactional
    public TagRelationVO createFromProposal(TagRelationDTO dto, int source, Long proposalId) {
        return doCreate(dto, source, proposalId);
    }

    private TagRelationVO doCreate(TagRelationDTO dto, int source, Long proposalId) {
        String relationType = dto.getRelationType().trim().toUpperCase(Locale.ROOT);
        if (!TagRelationConstants.TYPES.contains(relationType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "关系类型不合法");
        }
        Long fromTagId = dto.getFromTagId();
        Long toTagId = dto.getToTagId();
        if (fromTagId.equals(toTagId)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "关系不能指向自己");
        }
        Map<Long, Tag> tags = requireTags(fromTagId, toTagId);
        if (exists(fromTagId, toTagId, relationType)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "关系已存在");
        }
        // SIMILAR_TO 语义无向，反向也算重复
        if (TagRelationConstants.SIMILAR_TO.equals(relationType) && exists(toTagId, fromTagId, relationType)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "关系已存在");
        }
        if (TagRelationConstants.ACYCLIC_TYPES.contains(relationType)) {
            List<TagRelation> sameType = tagRelationMapper.selectList(
                    new LambdaQueryWrapper<TagRelation>().eq(TagRelation::getRelationType, relationType));
            if (TagRelationValidator.createsCycle(sameType, fromTagId, toTagId)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "该关系会形成环");
            }
        }
        TagRelation relation = TagRelation.builder()
                .fromTagId(fromTagId)
                .toTagId(toTagId)
                .relationType(relationType)
                .source(source)
                .proposalId(proposalId)
                .build();
        tagRelationMapper.insert(relation);
        sendEventAfterCommit(new TagRelationChangedEvent(payload(relation, TagRelationChangedPayload.ACTION_CREATED)));
        return toVO(relation, tags);
    }

    @Override
    @Transactional
    public void delete(Long relationId) {
        TagRelation relation = tagRelationMapper.selectById(relationId);
        if (relation == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "关系不存在");
        }
        tagRelationMapper.deleteById(relationId);
        sendEventAfterCommit(new TagRelationChangedEvent(payload(relation, TagRelationChangedPayload.ACTION_DELETED)));
    }

    private Map<Long, Tag> requireTags(Long fromTagId, Long toTagId) {
        List<Tag> tags = tagMapper.selectBatchIds(List.of(fromTagId, toTagId));
        if (tags.size() != 2) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "标签不存在");
        }
        return tags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
    }

    private void requireTag(Long tagId) {
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "标签不存在");
        }
    }

    private boolean exists(Long fromTagId, Long toTagId, String relationType) {
        return tagRelationMapper.exists(new LambdaQueryWrapper<TagRelation>()
                .eq(TagRelation::getFromTagId, fromTagId)
                .eq(TagRelation::getToTagId, toTagId)
                .eq(TagRelation::getRelationType, relationType));
    }

    private List<TagRelationVO> toVOs(List<TagRelation> relations) {
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> tagIds = relations.stream()
                .flatMap(relation -> Stream.of(relation.getFromTagId(), relation.getToTagId()))
                .collect(Collectors.toSet());
        Map<Long, String> nameById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return relations.stream()
                .map(relation -> TagRelationVO.builder()
                        .id(relation.getId())
                        .fromTagId(relation.getFromTagId())
                        .fromName(nameById.get(relation.getFromTagId()))
                        .toTagId(relation.getToTagId())
                        .toName(nameById.get(relation.getToTagId()))
                        .relationType(relation.getRelationType())
                        .source(relation.getSource())
                        .proposalId(relation.getProposalId())
                        .createTime(relation.getCreateTime())
                        .build())
                .toList();
    }

    private TagRelationVO toVO(TagRelation relation, Map<Long, Tag> tags) {
        return TagRelationVO.builder()
                .id(relation.getId())
                .fromTagId(relation.getFromTagId())
                .fromName(tags.get(relation.getFromTagId()).getName())
                .toTagId(relation.getToTagId())
                .toName(tags.get(relation.getToTagId()).getName())
                .relationType(relation.getRelationType())
                .source(relation.getSource())
                .proposalId(relation.getProposalId())
                .createTime(relation.getCreateTime())
                .build();
    }

    private TagRelationChangedPayload payload(TagRelation relation, String action) {
        return TagRelationChangedPayload.builder()
                .relationId(relation.getId())
                .fromTagId(relation.getFromTagId())
                .toTagId(relation.getToTagId())
                .relationType(relation.getRelationType())
                .action(action)
                .source(relation.getSource())
                .proposalId(relation.getProposalId())
                .build();
    }

    private void sendEvent(BaseEvent<?> event) {
        if (event.getPayload() == null) {
            return;
        }
        try {
            eventPublisher.send(event);
        } catch (Exception e) {
            log.error("标签关系事件发送失败, eventType={}", event.getEventType(), e);
        }
    }

    /** 有事务就等提交后再发，回滚时不留「消息发了但数据没落」 */
    private void sendEventAfterCommit(BaseEvent<?> event) {
        if (event.getPayload() == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendEvent(event);
            }
        });
    }
}
