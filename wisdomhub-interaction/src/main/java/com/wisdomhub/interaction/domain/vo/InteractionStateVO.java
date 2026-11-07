package com.wisdomhub.interaction.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionStateVO {

    private Boolean liked;

    private Boolean collected;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;
}
