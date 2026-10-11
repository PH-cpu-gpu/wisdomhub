package com.wisdomhub.search.domain.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * user_index 的写入文档，字段与 resources/es/user_index.json 逐一对齐。
 * <p>
 * 只有对外展示的字段 —— email / phone / password 一概不在索引里，也就不会出现在搜索结果中。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserIndexDoc {

    private Long userId;

    private String username;

    /** completion 字段，写入时显式带上，取 username */
    private String suggest;

    private String avatar;

    private String bio;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
