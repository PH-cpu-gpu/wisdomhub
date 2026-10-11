package com.wisdomhub.search.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** 用户搜索结果项。索引里本来就没有 email / phone / password，这里也不可能带出来 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchVO {

    private Long userId;

    private String username;

    private String avatar;

    private String bio;

    /** key 取 username / bio */
    private Map<String, String> highlight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
