package com.wisdomhub.video.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.video.domain.dto.VideoPublishDTO;
import com.wisdomhub.video.domain.dto.VideoUpdateDTO;
import com.wisdomhub.video.domain.vo.MyVideoVO;
import com.wisdomhub.video.domain.vo.VideoDetailVO;
import com.wisdomhub.video.domain.vo.VideoListItemVO;
import com.wisdomhub.video.domain.vo.VideoTagVO;

import java.util.List;

public interface VideoService {

    Long publish(VideoPublishDTO dto);

    VideoDetailVO getDetail(Long videoId);

    PageResponse<VideoListItemVO> list(Integer page, Integer pageSize, Long authorId, Long tagId);

    PageResponse<MyVideoVO> mine(Integer page, Integer pageSize);

    /** 编辑标题、简介与标签，仅作者本人可操作 */
    void update(Long videoId, VideoUpdateDTO dto);

    /** 软删除视频并级联清理互动数据，仅作者本人可操作 */
    void delete(Long videoId);

    /** 下架已发布视频，仅作者本人可操作 */
    void offline(Long videoId);

    /** 重新发布已下架视频，仅作者本人可操作 */
    void online(Long videoId);

    /** 全量替换视频标签，仅作者本人可操作；返回替换后的标签 */
    List<VideoTagVO> updateTags(Long videoId, List<Long> tagIds);
}
