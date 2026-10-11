package com.wisdomhub.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.event.BaseEvent;
import com.wisdomhub.common.mq.event.TagCreatedEvent;
import com.wisdomhub.common.mq.event.TagDeletedEvent;
import com.wisdomhub.common.mq.event.TagUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.TagDeletedPayload;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.TagConstants;
import com.wisdomhub.content.domain.dto.TagDTO;
import com.wisdomhub.content.domain.entity.ArticleTag;
import com.wisdomhub.content.domain.entity.Tag;
import com.wisdomhub.content.domain.vo.HotTagVO;
import com.wisdomhub.content.domain.vo.TagVO;
import com.wisdomhub.content.mapper.ArticleTagMapper;
import com.wisdomhub.content.mapper.TagMapper;
import com.wisdomhub.content.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final EventPublisher eventPublisher;

    // ==================== 读：走两级缓存 ====================

    @Override
    @Cacheable(cacheNames = TagConstants.CACHE_TAG, key = "'all'")
    public List<TagVO> listAllTags() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId));
        List<TagVO> result = new ArrayList<>(tags.size());
        for (Tag tag : tags) {
            result.add(TagVO.builder()
                    .tagId(tag.getId())
                    .name(tag.getName())
                    .category(tag.getCategory())
                    .description(tag.getDescription())
                    .build());
        }
        return result;
    }

    @Override
    @Cacheable(cacheNames = TagConstants.CACHE_HOT_TAG, key = "#limit")
    public List<HotTagVO> listHotTags(Integer limit) {
        return tagMapper.selectHotTags(ArticleConstants.PUBLISH, ArticleConstants.NOT_DELETED, limit);
    }

    // ==================== 写：清 tag + tag:hot ====================
    // 三个写方法都清两个缓存

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = TagConstants.CACHE_TAG, allEntries = true),
            @CacheEvict(cacheNames = TagConstants.CACHE_HOT_TAG, allEntries = true)
    })
    public Long createTag(TagDTO dto) {
        CurrentUserUtil.id();
        String name = dto.getName().trim();
        if (tagMapper.exists(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "标签名已存在");
        }
        // 并发下预检可能同时通过，最终由 uk_name 抛 DuplicateKeyException → 全局 409
        Tag tag = Tag.builder()
                .name(name)
                .category(StrUtil.blankToDefault(dto.getCategory(), TagConstants.CATEGORY_DEFAULT).trim())
                .description(StrUtil.nullToEmpty(dto.getDescription()).trim())
                .build();
        tagMapper.insert(tag);
        sendEvent(new TagCreatedEvent(loadTagPayload(tag.getId())));
        return tag.getId();
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = TagConstants.CACHE_TAG, allEntries = true),
            @CacheEvict(cacheNames = TagConstants.CACHE_HOT_TAG, allEntries = true)
    })
    public void updateTag(Long tagId, TagDTO dto) {
        CurrentUserUtil.id();
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "标签不存在");
        }
        String name = dto.getName().trim();
        if (tagMapper.exists(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name)
                .ne(Tag::getId, tagId))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "标签名已存在");
        }
        tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                .set(Tag::getName, name)
                .set(Tag::getCategory, StrUtil.blankToDefault(dto.getCategory(), TagConstants.CATEGORY_DEFAULT).trim())
                .set(Tag::getDescription, StrUtil.nullToEmpty(dto.getDescription()).trim())
                .set(Tag::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Tag::getId, tagId));
        sendEvent(new TagUpdatedEvent(loadTagPayload(tagId)));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = TagConstants.CACHE_TAG, allEntries = true),
            @CacheEvict(cacheNames = TagConstants.CACHE_HOT_TAG, allEntries = true)
    })
    public void deleteTag(Long tagId) {
        CurrentUserUtil.id();
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "标签不存在");
        }
        // 顺序：先删关系行，再删标签本体。
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
        tagMapper.deleteById(tagId);
        // 标签是物理删，行没了就再也查不到 —— 这条事件是下游清 ES 里残留标签的唯一线索
        sendEvent(new TagDeletedEvent(TagDeletedPayload.builder().tagId(tagId).build()));
    }

    // ==================== 事件 ====================

    /** 写完回查一次再拼 payload，保证发出去的就是已提交的那一行 */
    private TagEventPayload loadTagPayload(Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            return null;
        }
        return TagEventPayload.builder()
                .tagId(tag.getId())
                .name(tag.getName())
                .category(tag.getCategory())
                .description(tag.getDescription())
                .createTime(tag.getCreateTime())
                .updateTime(tag.getUpdateTime())
                .build();
    }

    /** 发失败只记日志：写已经生效，抛出去会变成「接口 500 但标签其实改好了」 */
    private void sendEvent(BaseEvent<?> event) {
        if (event.getPayload() == null) {
            return;
        }
        try {
            eventPublisher.send(event);
        } catch (Exception e) {
            log.error("标签事件发送失败, eventType={}", event.getEventType(), e);
        }
    }

    // ==================== 供发布流程调用 ====================

    @Override
    public List<Long> normalizeTagIds(List<Long> tagIds) {
        List<Long> distinct = new ArrayList<>();
        if (tagIds == null) {
            return distinct;
        }
        for (Long id : tagIds) {
            // 重复与 null 是客户端噪音，静默剔除；但「标签不存在」是语义错误，要 400
            if (id != null && !distinct.contains(id)) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return distinct;
        }
        Long found = tagMapper.selectCount(new LambdaQueryWrapper<Tag>().in(Tag::getId, distinct));
        if (found == null || found != distinct.size()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "包含不存在的标签");
        }
        return distinct;
    }

    @Override
    @CacheEvict(cacheNames = TagConstants.CACHE_HOT_TAG, allEntries = true)
    public void evictHotTags() {
        // 只清热门：标签字典（名字、描述）没变，变的是各标签下的文章数
    }
}
