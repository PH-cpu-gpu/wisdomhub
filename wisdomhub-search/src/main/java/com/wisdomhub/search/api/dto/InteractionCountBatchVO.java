package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class InteractionCountBatchVO {

    private List<InteractionCountVO> items;
}
