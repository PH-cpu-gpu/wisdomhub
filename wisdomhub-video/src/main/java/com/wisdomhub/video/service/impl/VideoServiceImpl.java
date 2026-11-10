package com.wisdomhub.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.video.constants.MediaAssetConstants;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.constants.VideoRedisKeyConstants;
import com.wisdomhub.video.domain.dto.VideoPublishDTO;
import com.wisdomhub.video.domain.dto.VideoUpdateDTO;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.mq.VideoBehaviorEventPublisher;
import com.wisdomhub.video.mq.VideoEventSender;
import com.wisdomhub.video.domain.vo.MyVideoVO;
import com.wisdomhub.video.domain.vo.VideoDetailVO;
import com.wisdomhub.video.domain.vo.VideoListItemVO;
import com.wisdomhub.video.domain.vo.VideoTagVO;
import com.wisdomhub.video.mapper.MediaAssetMapper;
import com.wisdomhub.video.mapper.VideoMapper;
import com.wisdomhub.video.service.VideoPlaybackService;
import com.wisdomhub.video.service.VideoService;
import com.wisdomhub.video.service.VideoTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoPlaybackService videoPlaybackService;
    private final VideoTagService videoTagService;
    private final VideoEventSender videoEventSender;
    private final VideoBehaviorEventPublisher videoBehaviorEventPublisher;

    @Override
    @Transactional
    public Long publish(VideoPublishDTO dto) {
        Long authorId = CurrentUserUtil.id();
        // 标签先校验：不存在时直接 400，不留下半条视频
        List<Long> tagIds = videoTagService.normalizeAndValidate(dto.getTagIds());
        MediaAsset asset = mediaAssetMapper.selectById(dto.getAssetId());
        if (asset == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频资产不存在");
        }
        if (!authorId.equals(asset.getOwnerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权发布他人的视频");
        }
        if (MediaAssetConstants.REJECTED.equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频审核未通过");
        }
        if (MediaAssetConstants.FAILED.equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频处理失败");
        }
        if (MediaAssetConstants.REVIEWING.equals(asset.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频审核中，请稍后再试");
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
        // 同库本地事务：标签失败时视频一起回滚
        videoTagService.replace(video.getId(), tagIds);
        VideoEventPayload payload = loadPayload(video.getId());
        if (payload != null) {
            videoEventSender.createdAfterCommit(payload);
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
                .tags(videoTagService.tagsOf(video.getId()))
                .build();
    }

    @Override
    public PageResponse<VideoListItemVO> list(Integer page, Integer pageSize, Long authorId, Long tagId) {
        Page<Video> result = videoMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Video>()
                        .select(Video::getId, Video::getAuthorId, Video::getTitle, Video::getSummary,
                                Video::getCoverKey, Video::getDurationMs, Video::getViewCount,
                                Video::getPublishTime)
                        .eq(Video::getStatus, VideoConstants.PUBLISHED)
                        .eq(authorId != null, Video::getAuthorId, authorId)
                        // 可选筛选：条件为 false 时整条不拼进 SQL
                        .inSql(tagId != null, Video::getId,
                                "SELECT video_id FROM video_tag WHERE tag_id = " + tagId)
                        .orderByDesc(Video::getPublishTime)
                        .orderByDesc(Video::getId));
        Map<Long, List<VideoTagVO>> tagsByVideoId = videoTagService.tagsByVideoIds(
                result.getRecords().stream().map(Video::getId).toList());
        List<VideoListItemVO> items = result.getRecords().stream()
                .map(video -> toListItem(video, tagsByVideoId.getOrDefault(video.getId(), List.of())))
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
        Map<Long, List<VideoTagVO>> tagsByVideoId = videoTagService.tagsByVideoIds(
                result.getRecords().stream().map(Video::getId).toList());
        List<MyVideoVO> items = result.getRecords().stream()
                .map(video -> toMyVideo(video, tagsByVideoId.getOrDefault(video.getId(), List.of())))
                .toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    @Transactional
    public void update(Long videoId, VideoUpdateDTO dto) {
        requireOwned(videoId);
        List<Long> tagIds = videoTagService.normalizeAndValidate(dto.getTagIds());
        String summary = dto.getSummary() == null || dto.getSummary().isBlank() ? null : dto.getSummary().trim();
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getTitle, dto.getTitle().trim())
                .set(Video::getSummary, summary)
                .set(Video::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Video::getId, videoId));
        videoTagService.replace(videoId, tagIds);
        VideoEventPayload payload = loadPayload(videoId);
        if (payload != null) {
            videoEventSender.updatedAfterCommit(payload);
        }
    }

    @Override
    @Transactional
    public void delete(Long videoId) {
        Video video = requireOwned(videoId);
        int affected = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, VideoConstants.DELETED)
                .set(Video::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, video.getStatus()));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频状态已变化，删除未生效");
        }
        videoTagService.replace(videoId, List.of());
        deleteViewCountKey(videoId);
        videoEventSender.deletedAfterCommit(videoId, video.getAuthorId());
    }

    @Override
    @Transactional
    public void offline(Long videoId) {
        Video video = requireOwned(videoId);
        if (!VideoConstants.PUBLISHED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "只有已发布视频可以下架");
        }
        int affected = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, VideoConstants.OFFLINE)
                .set(Video::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, VideoConstants.PUBLISHED));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频状态已变化，下架未生效");
        }
        sendUpdatedAfterCommit(videoId);
    }

    @Override
    @Transactional
    public void online(Long videoId) {
        Video video = requireOwned(videoId);
        if (!VideoConstants.OFFLINE.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "只有已下架视频可以重新发布");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int affected = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, VideoConstants.PUBLISHED)
                .set(Video::getPublishTime, now)
                .set(Video::getUpdateTime, now)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, VideoConstants.OFFLINE));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "视频状态已变化，重新发布未生效");
        }
        sendUpdatedAfterCommit(videoId);
    }

    private void sendUpdatedAfterCommit(Long videoId) {
        VideoEventPayload payload = loadPayload(videoId);
        if (payload != null) {
            videoEventSender.updatedAfterCommit(payload);
        }
    }

    @Override
    @Transactional
    public List<VideoTagVO> updateTags(Long videoId, List<Long> tagIds) {
        requireOwned(videoId);
        List<Long> normalized = videoTagService.normalizeAndValidate(tagIds);
        videoTagService.replace(videoId, normalized);
        // 标签变更不经过 updateById，水位线对账靠这里手动 bump
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Video::getId, videoId));
        VideoEventPayload payload = loadPayload(videoId);
        if (payload != null) {
            videoEventSender.updatedAfterCommit(payload);
        }
        return videoTagService.tagsOf(videoId);
    }

    private Video requireOwned(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        OwnerUtil.assertOwner(video.getAuthorId());
        return video;
    }

    private VideoEventPayload loadPayload(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            return null;
        }
        return VideoEventPayload.builder()
                .videoId(video.getId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .status(video.getStatus())
                .publishTime(video.getPublishTime())
                .updateTime(video.getUpdateTime())
                .build();
    }

    private void deleteViewCountKey(Long videoId) {
        try {
            stringRedisTemplate.delete(VideoRedisKeyConstants.VIEW_COUNT_KEY + videoId);
        } catch (Exception e) {
            log.warn("播放量缓存清理失败, videoId={}", videoId, e);
        }
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
            if (latest != null) {
                videoBehaviorEventPublisher.viewRecorded(video.getId(), userId);
            }
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

    private VideoListItemVO toListItem(Video video, List<VideoTagVO> tags) {
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
                .tags(tags)
                .build();
    }

    private MyVideoVO toMyVideo(Video video, List<VideoTagVO> tags) {
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
                .tags(tags)
                .build();
    }
}
