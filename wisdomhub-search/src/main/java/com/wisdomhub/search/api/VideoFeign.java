package com.wisdomhub.search.api;

import com.wisdomhub.search.api.dto.VideoInternalDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** video-service 的内部读接口；/internal/video/{id} 不允许加缓存。 */
@FeignClient(name = "video-service", contextId = "searchVideo", path = "/internal/video")
public interface VideoFeign {

    @GetMapping("/{videoId}")
    VideoInternalDTO getVideo(@PathVariable("videoId") Long videoId);

    /** since 用 ISO 字符串传，避免 Feign 对 LocalDateTime 的编码差异；全量扫传 1970-01-01T00:00:00 */
    @GetMapping("/sync")
    List<VideoInternalDTO> syncVideos(@RequestParam("since") String since,
                                      @RequestParam("lastId") Long lastId,
                                      @RequestParam("size") Integer size);
}
