package com.wisdomhub.interaction.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 我的收藏一页里的一行：只给目标和收藏时间，内容本体由各内容服务回填 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteItemVO {

    private Integer targetType;

    private Long targetId;

    private LocalDateTime createTime;
}
