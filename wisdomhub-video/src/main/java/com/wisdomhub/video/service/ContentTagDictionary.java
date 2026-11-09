package com.wisdomhub.video.service;

import com.wisdomhub.common.feign.client.ContentTagFeign;
import com.wisdomhub.common.feign.dto.ContentTagDTO;
import com.wisdomhub.video.constants.VideoTagConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * content 标签字典的只读视图。
 *
 * <p>内容侧已对 /internal/content/tag/all 做过缓存，这里再套一层视频服务的两级缓存，
 * 让上下列表页只在缓存失效后打一次远端。标签治理仍在 content，本类不提供任何写操作。
 */
@Component
@RequiredArgsConstructor
public class ContentTagDictionary {

    private final ContentTagFeign contentTagFeign;

    /** 全量标签。远端异常直接抛（由 FeignExceptionHandler 转 503），是否降级由调用方决定 */
    @Cacheable(cacheNames = VideoTagConstants.CACHE_TAG, key = "'all'")
    public List<ContentTagDTO> all() {
        List<ContentTagDTO> tags = contentTagFeign.listAllTags();
        // 不返回 List.of()：JDK 不可变集合带默认类型信息无法反序列化，会直接把缓存读坏
        return tags == null ? new ArrayList<>() : tags;
    }
}
