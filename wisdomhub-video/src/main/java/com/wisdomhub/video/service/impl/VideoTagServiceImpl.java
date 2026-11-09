package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.feign.dto.ContentTagDTO;
import com.wisdomhub.video.constants.VideoTagConstants;
import com.wisdomhub.video.domain.entity.VideoTag;
import com.wisdomhub.video.domain.vo.VideoTagVO;
import com.wisdomhub.video.mapper.VideoTagMapper;
import com.wisdomhub.video.service.ContentTagDictionary;
import com.wisdomhub.video.service.VideoTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTagServiceImpl implements VideoTagService {

    private final VideoTagMapper videoTagMapper;
    private final ContentTagDictionary contentTagDictionary;

    @Override
    public List<Long> normalizeAndValidate(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        // 重复与 null 是客户端噪音，静默剔除；「标签不存在」是语义错误，要 400
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId != null) {
                distinct.add(tagId);
            }
        }
        if (distinct.size() > VideoTagConstants.MAX_TAGS_PER_VIDEO) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST,
                    "一个视频最多选择" + VideoTagConstants.MAX_TAGS_PER_VIDEO + "个标签");
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        // 写路径不降级：字典拿不到就让发布/改标签失败，避免落进不存在的标签
        Map<Long, ContentTagDTO> dictionary = dictionaryById();
        for (Long tagId : distinct) {
            if (!dictionary.containsKey(tagId)) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "标签不存在: " + tagId);
            }
        }
        return new ArrayList<>(distinct);
    }

    @Override
    public void replace(Long videoId, List<Long> tagIds) {
        videoTagMapper.delete(new LambdaQueryWrapper<VideoTag>().eq(VideoTag::getVideoId, videoId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            videoTagMapper.insert(VideoTag.builder()
                    .videoId(videoId)
                    .tagId(tagId)
                    .build());
        }
    }

    @Override
    public List<Long> tagIdsOf(Long videoId) {
        return videoTagMapper.selectList(new LambdaQueryWrapper<VideoTag>()
                        .select(VideoTag::getTagId)
                        .eq(VideoTag::getVideoId, videoId)
                        .orderByAsc(VideoTag::getId))
                .stream().map(VideoTag::getTagId).toList();
    }

    @Override
    public Map<Long, List<VideoTagVO>> tagsByVideoIds(List<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Map.of();
        }
        List<VideoTag> rows = videoTagMapper.selectList(new LambdaQueryWrapper<VideoTag>()
                .select(VideoTag::getVideoId, VideoTag::getTagId)
                .in(VideoTag::getVideoId, videoIds)
                .orderByAsc(VideoTag::getId));
        if (rows.isEmpty()) {
            return Map.of();
        }
        // 读路径降级：content 抖动时详情/列表仍可看，只是标签为空
        Map<Long, ContentTagDTO> dictionary = dictionaryOrEmpty();
        if (dictionary.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<VideoTagVO>> result = new HashMap<>();
        for (VideoTag row : rows) {
            ContentTagDTO tag = dictionary.get(row.getTagId());
            if (tag == null) {
                // 标签被删、本地关联还没清干净的窗口期，跳过比抛异常合适
                continue;
            }
            result.computeIfAbsent(row.getVideoId(), k -> new ArrayList<>())
                    .add(VideoTagVO.builder()
                            .tagId(tag.getTagId())
                            .name(tag.getName())
                            .category(tag.getCategory())
                            .build());
        }
        return result;
    }

    @Override
    public List<VideoTagVO> tagsOf(Long videoId) {
        return tagsByVideoIds(List.of(videoId)).getOrDefault(videoId, List.of());
    }

    private Map<Long, ContentTagDTO> dictionaryOrEmpty() {
        try {
            return dictionaryById();
        } catch (RuntimeException e) {
            // 只在 WARN 留一行：列表页每条视频都会走到这里，带栈会把日志冲满
            log.warn("标签字典不可用，读路径按无标签降级: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, ContentTagDTO> dictionaryById() {
        Map<Long, ContentTagDTO> result = new HashMap<>();
        for (ContentTagDTO tag : contentTagDictionary.all()) {
            if (tag != null && tag.getTagId() != null) {
                result.put(tag.getTagId(), tag);
            }
        }
        return result;
    }
}
