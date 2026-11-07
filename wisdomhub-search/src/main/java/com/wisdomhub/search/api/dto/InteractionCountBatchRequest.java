package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class InteractionCountBatchRequest {

    private List<InteractionCountQueryDTO> items;
}
