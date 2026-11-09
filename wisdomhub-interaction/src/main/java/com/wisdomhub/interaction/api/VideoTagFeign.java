package com.wisdomhub.interaction.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/** video-service 的视频标签 ID 接口，行为事件需要 tagIds。 */
@FeignClient(name = "video-service", contextId = "interactionVideoTag", path = "/internal/video")
public interface VideoTagFeign {

    @GetMapping("/{videoId}/tag/ids")
    List<Long> listTagIds(@PathVariable("videoId") Long videoId);
}
