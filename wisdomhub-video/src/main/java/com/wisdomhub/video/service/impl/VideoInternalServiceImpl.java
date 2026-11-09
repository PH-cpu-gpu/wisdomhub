package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.domain.vo.VideoInternalVO;
import com.wisdomhub.video.domain.vo.VideoTagVO;
import com.wisdomhub.video.mapper.VideoMapper;
import com.wisdomhub.video.service.VideoInternalService;
import com.wisdomhub.video.service.VideoTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoInternalServiceImpl implements VideoInternalService {

    private static final int SYNC_DEFAULT_SIZE = 500;

    private static final int SYNC_MAX_SIZE = 500;

    private final VideoMapper videoMapper;
    private final VideoTagService videoTagService;

    @Override
    public VideoInternalVO getVideo(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        return toVO(video, videoTagService.tagsOf(videoId));
    }

    @Override
    public List<Long> tagIdsOf(Long videoId) {
        return videoTagService.tagIdsOf(videoId);
    }

    @Override
    public List<VideoInternalVO> syncVideos(LocalDateTime since, Long lastId, Integer size) {
        int limit = (size == null || size <= 0) ? SYNC_DEFAULT_SIZE : Math.min(size, SYNC_MAX_SIZE);
        long cursor = lastId == null ? 0L : lastId;
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .ne(Video::getStatus, VideoConstants.DELETED);
        if (since == null) {
            wrapper.gt(Video::getId, cursor).orderByAsc(Video::getId);
        } else {
            // (update_time, id) 复合游标，同一秒多行不漏
            wrapper.and(q -> q.gt(Video::getUpdateTime, since)
                            .or(o -> o.eq(Video::getUpdateTime, since).gt(Video::getId, cursor)))
                    .orderByAsc(Video::getUpdateTime)
                    .orderByAsc(Video::getId);
        }
        List<Video> videos = videoMapper.selectPage(new Page<>(1, limit, false), wrapper).getRecords();
        if (videos.isEmpty()) {
            return List.of();
        }
        Map<Long, List<VideoTagVO>> tagsByVideoId = videoTagService.tagsByVideoIds(
                videos.stream().map(Video::getId).toList());
        return videos.stream()
                .map(video -> toVO(video, tagsByVideoId.getOrDefault(video.getId(), List.of())))
                .toList();
    }

    private VideoInternalVO toVO(Video video, List<VideoTagVO> tags) {
        return VideoInternalVO.builder()
                .videoId(video.getId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .coverKey(video.getCoverKey())
                .durationMs(video.getDurationMs())
                .width(video.getWidth())
                .height(video.getHeight())
                .status(video.getStatus())
                .viewCount(video.getViewCount())
                .createTime(video.getCreateTime())
                .publishTime(video.getPublishTime())
                .updateTime(video.getUpdateTime())
                .tags(tags)
                .build();
    }
}
