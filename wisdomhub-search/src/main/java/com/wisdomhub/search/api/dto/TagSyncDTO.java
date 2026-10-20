package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应 content-service 的 TagSyncVO（本服务只消费，不反向定义契约）。
 */
@Data
public class TagSyncDTO {

    private Long tagId;

    private String name;

    private String category;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
