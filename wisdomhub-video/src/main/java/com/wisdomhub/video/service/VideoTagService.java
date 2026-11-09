package com.wisdomhub.video.service;

import com.wisdomhub.video.domain.vo.VideoTagVO;

import java.util.List;
import java.util.Map;

public interface VideoTagService {

    /** 去 null、去重、保序并校验标签存在；超过上限或标签不存在抛 400 */
    List<Long> normalizeAndValidate(List<Long> tagIds);

    /** 全量替换某视频的标签关联（先删后插），调用方负责事务 */
    void replace(Long videoId, List<Long> tagIds);

    /** 只取标签 ID，供行为事件使用，不查 content 字典 */
    List<Long> tagIdsOf(Long videoId);

    /** 批量取一页视频的标签，避免逐条查字典；字典不可用时退化为空标签 */
    Map<Long, List<VideoTagVO>> tagsByVideoIds(List<Long> videoIds);

    /** 单视频标签，发布/改标签后回显用 */
    List<VideoTagVO> tagsOf(Long videoId);
}
