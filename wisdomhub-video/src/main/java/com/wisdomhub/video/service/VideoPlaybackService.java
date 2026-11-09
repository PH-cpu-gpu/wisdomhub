package com.wisdomhub.video.service;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.video.config.PlaybackProperties;
import com.wisdomhub.video.constants.VideoConstants;
import com.wisdomhub.video.domain.entity.Video;
import com.wisdomhub.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPlaybackService {

    public static final String DEFAULT_COVER_PATH = "/api/video/default-cover.svg";

    private final VideoMapper videoMapper;
    private final CosObjectService cosObjectService;
    private final CosPresignService cosPresignService;
    private final PlaybackProperties playbackProperties;

    public String playlistUrl(Long videoId) {
        return "/api/video/" + videoId + "/playlist";
    }

    /** 稳定封面入口：按 id 取签名 URL，404 由调用方转成状态码。 */
    public String coverUrlById(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        return coverUrl(video);
    }

    public String coverUrl(Video video) {
        String coverKey = video.getCoverKey();
        if (coverKey == null || coverKey.isBlank()) {
            return DEFAULT_COVER_PATH;
        }
        try {
            return cosPresignService.presignGet(coverKey, playbackProperties.getUrlTtlSeconds());
        } catch (Exception e) {
            log.warn("封面签名失败，使用兜底封面, videoId={}, coverKey={}",
                    video.getId(), coverKey, e);
            return DEFAULT_COVER_PATH;
        }
    }

    public String buildPlaylist(Long videoId) {
        Video video = requirePlayable(videoId);
        String playKey = video.getPlayKey();
        if (playKey == null || playKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "播放列表不存在");
        }

        String raw;
        try {
            raw = cosObjectService.readString(playKey);
        } catch (Exception e) {
            log.error("读取 HLS 播放列表失败, videoId={}, playKey={}", videoId, playKey, e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "播放列表暂不可用");
        }
        if (raw == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "播放列表不存在");
        }
        return rewrite(raw, playKey);
    }

    private Video requirePlayable(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || VideoConstants.DELETED.equals(video.getStatus())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        Long userId = UserContext.getUserId();
        if (!VideoConstants.PUBLISHED.equals(video.getStatus())
                && !video.getAuthorId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "视频不存在");
        }
        return video;
    }

    private String rewrite(String raw, String playKey) {
        String baseDir = playKey.substring(0, playKey.lastIndexOf('/') + 1);
        int ttl = playbackProperties.getUrlTtlSeconds();
        String[] lines = raw.split("\n", -1);
        StringBuilder out = new StringBuilder(raw.length() + lines.length * 160);
        for (String line : lines) {
            String clean = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            String uri = clean.strip();
            if (uri.isEmpty() || uri.startsWith("#")
                    || uri.startsWith("http://") || uri.startsWith("https://")) {
                out.append(clean).append('\n');
                continue;
            }
            int queryIndex = uri.indexOf('?');
            String pathPart = queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
            String query = queryIndex >= 0 ? uri.substring(queryIndex) : "";
            String key = pathPart.startsWith("/") ? pathPart.substring(1) : baseDir + pathPart;
            out.append(cosPresignService.presignGet(key, ttl)).append(query).append('\n');
        }
        return out.toString();
    }
}
