package com.wisdomhub.video.service;

import com.wisdomhub.video.domain.vo.VideoInternalVO;

import java.time.LocalDateTime;
import java.util.List;

/** 只给 search 用的内部读接口，不走缓存。 */
public interface VideoInternalService {

    VideoInternalVO getVideo(Long videoId);

    List<VideoInternalVO> syncVideos(LocalDateTime since, Long lastId, Integer size);

    List<Long> tagIdsOf(Long videoId);
}
