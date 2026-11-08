package com.wisdomhub.video.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.video.domain.dto.VideoPublishDTO;
import com.wisdomhub.video.domain.vo.MyVideoVO;
import com.wisdomhub.video.domain.vo.VideoDetailVO;
import com.wisdomhub.video.domain.vo.VideoListItemVO;

public interface VideoService {

    Long publish(VideoPublishDTO dto);

    VideoDetailVO getDetail(Long videoId);

    PageResponse<VideoListItemVO> list(Integer page, Integer pageSize, Long authorId);

    PageResponse<MyVideoVO> mine(Integer page, Integer pageSize);
}
