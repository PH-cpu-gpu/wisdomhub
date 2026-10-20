package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 标签对账快照，供 search-service 修 tag_index。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagSyncVO {

    private Long tagId;

    private String name;

    private String category;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
