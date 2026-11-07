package com.wisdomhub.recommendation.api.interaction;

import com.wisdomhub.recommendation.api.interaction.dto.InteractionCountBatchRequest;
import com.wisdomhub.recommendation.api.interaction.dto.InteractionCountBatchVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** interaction-service 的内部批量计数接口。 */
@FeignClient(name = "interaction-service", contextId = "recommendationInteractionCount",
        path = "/internal/interaction")
public interface InteractionCountFeign {

    @PostMapping("/counts")
    InteractionCountBatchVO counts(@RequestBody InteractionCountBatchRequest request);
}
