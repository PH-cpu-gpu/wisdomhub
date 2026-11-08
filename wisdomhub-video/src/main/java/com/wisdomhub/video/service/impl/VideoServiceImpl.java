package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.video.constants.MediaAssetConstants;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.constants.VideoRedisKeyConstants;
import com.wisdomhub.video.domain.dto.VideoPublishDTO;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.domain.vo.MyVideoVO;
import com.wisdomhub.video.domain.vo.VideoDetailVO;
import com.wisdomhub.video.domain.vo.VideoListItemVO;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import com.wisdomhub.video.mapper.VideoMapper;
import com.wisdomhub.video.service.VideoPlaybackService;
import com.wisdomhub.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoPlaybackService videoPlaybackService;

    @Override
    public Long publish(VideoPublishDTO dto) {
        Long authorId = CurrentUserUtil.id();
        MediaAsset asset = mediaAssetMapper.selectById(dto.getAssetId());
        if (asset == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频资产不存在");
        }
        if (!authorId.equals(asset.getOwnerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权发布他人的视频");
        }
        if (!MediaAssetConstants.READY.equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频尚未就绪");
        }
        Long exists = videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getAssetId, asset.getId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该视频已发布");
        }

        String summary = dto.getSummary() == null || dto.getSummary().isBlank() ? null : dto.getSummary().trim();
        Video video = Video.builder()
                .assetId(asset.getId())
                .authorId(authorId)
                .title(dto.getTitle().trim())
                .summary(summary)
                .coverKey(asset.getCoverKey())
                .playKey(buildPlayKey(asset))
                .durationMs(asset.getDurationMs())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .viewCount(0)
                .status(VideoConstants.PUBLISHED)
                .publishTime(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        try {
            videoMapper.insert(video);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该视频已发布");
        }
        return video.getId();
    }

    @Override
    public VideoDetailVO getDetail(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        Long userId = UserContext.getUserId();
        if (!VideoConstants.PUBLISHED.equals(video.getStatus())
                && !video.getAuthorId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }

        int viewCount = VideoConstants.PUBLISHED.equals(video.getStatus())
                ? resolveViewCount(video)
                : snapshotViewCount(video);
        return VideoDetailVO.builder()
                .videoId(video.getId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .coverKey(video.getCoverKey())
                .coverUrl(videoPlaybackService.coverUrl(video))
                .playlistUrl(videoPlaybackService.playlistUrl(video.getId()))
                .durationMs(video.getDurationMs())
                .width(video.getWidth())
                .height(video.getHeight())
                .viewCount(viewCount)
                .status(video.getStatus())
                .publishTime(video.getPublishTime())
                .build();
    }

    @Override
    public PageResponse<VideoListItemVO> list(Integer page, Integer pageSize, Long authorId) {
        Page<Video> result = videoMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Video>()
                        .select(Video::getId, Video::getAuthorId, Video::getTitle, Video::getSummary,
                                Video::getCoverKey, Video::getDurationMs, Video::getViewCount,
                                Video::getPublishTime)
                        .eq(Video::getStatus, VideoConstants.PUBLISHED)
                        .eq(authorId != null, Video::getAuthorId, authorId)
                        .orderByDesc(Video::getPublishTime)
                        .orderByDesc(Video::getId));
        List<VideoListItemVO> items = result.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    public PageResponse<MyVideoVO> mine(Integer page, Integer pageSize) {
        Long authorId = CurrentUserUtil.id();
        Page<Video> result = videoMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getAuthorId, authorId)
                        .ne(Video::getStatus, VideoConstants.DELETED)
                        .orderByDesc(Video::getCreateTime)
                        .orderByDesc(Video::getId));
        List<MyVideoVO> items = result.getRecords().stream()
                .map(this::toMyVideo)
                .toList();
        return PageUtil.toResponse(result, items);
    }

    private String buildPlayKey(MediaAsset asset) {
        return "hls/" + asset.getOwnerId() + "/" + asset.getId() + "/index.m3u8";
    }

    private int resolveViewCount(Video video) {
        String countKey = VideoRedisKeyConstants.VIEW_COUNT_KEY + video.getId();
        try {
            int current = ensureCount(countKey, () -> (long) snapshotViewCount(video));
            Long userId = UserContext.getUserId();
            if (userId == null) {
                return current;
            }
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                    VideoRedisKeyConstants.VIEW_ONCE_KEY + userId + ":" + video.getId(),
                    "1", RedisTtl.VIEW_ONCE);
            if (!Boolean.TRUE.equals(first)) {
                return current;
            }
            Long latest = stringRedisTemplate.opsForValue().increment(countKey);
            return latest == null ? current : Math.toIntExact(latest);
        } catch (Exception e) {
            log.warn("播放量读取失败, 退回 video.view_count 快照: videoId={}", video.getId(), e);
            return snapshotViewCount(video);
        }
    }

    private int ensureCount(String key, Supplier<Long> dbCount) {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        int count = Math.toIntExact(dbCount.get());
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(count));
        return count;
    }

    private int snapshotViewCount(Video video) {
        return video.getViewCount() == null ? 0 : video.getViewCount();
    }

    private VideoListItemVO toListItem(Video video) {
        return VideoListItemVO.builder()
                .videoId(video.getId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .coverKey(video.getCoverKey())
                .coverUrl(videoPlaybackService.coverUrl(video))
                .durationMs(video.getDurationMs())
                .viewCount(video.getViewCount())
                .publishTime(video.getPublishTime())
                .build();
    }

    private MyVideoVO toMyVideo(Video video) {
        return MyVideoVO.builder()
                .videoId(video.getId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .coverKey(video.getCoverKey())
                .coverUrl(videoPlaybackService.coverUrl(video))
                .durationMs(video.getDurationMs())
                .viewCount(video.getViewCount())
                .status(video.getStatus())
                .publishTime(video.getPublishTime())
                .createTime(video.getCreateTime())
                .build();
    }
}
