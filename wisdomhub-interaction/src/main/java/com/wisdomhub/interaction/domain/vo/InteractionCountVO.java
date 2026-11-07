package com.wisdomhub.interaction.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionCountVO {

    private Integer targetType;

    private Long targetId;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
