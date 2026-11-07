package com.wisdomhub.recommendation.api.interaction.dto;

import lombok.Data;

import java.util.List;

@Data
public class InteractionCountBatchVO {

    private List<InteractionCountVO> items;
}
