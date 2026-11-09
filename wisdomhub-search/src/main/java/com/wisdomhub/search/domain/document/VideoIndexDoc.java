package com.wisdomhub.search.domain.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * video_index 的写入文档，字段与 resources/es/video_index.json 逐一对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoIndexDoc {

    private Long videoId;

    private String title;

    /** completion 字段，输入建议用。*/
    private String suggest;

    private String summary;

    private Long authorId;

    private List<Tag> tags;

    private String coverKey;

    private Long durationMs;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime publishTime;

    private LocalDateTime updateTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tag {

        private Long tagId;

        private String name;

        private String category;
    }
}
