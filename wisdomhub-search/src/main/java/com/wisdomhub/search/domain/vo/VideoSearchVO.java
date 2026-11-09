package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 视频搜索结果项。只给 authorId，昵称头像由前端批量调 user-service。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoSearchVO {

    private Long videoId;

    private Long authorId;

    private String title;

    private String summary;

    /** key 取 title / summary，value 是带 <em> 的已转义片段 */
    private Map<String, String> highlight;

    private List<TagBriefVO> tags;

    private String coverKey;

    /** 稳定入口，302 到签名 URL；索引里不存会过期的签名地址 */
    private String coverUrl;

    private Long durationMs;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
