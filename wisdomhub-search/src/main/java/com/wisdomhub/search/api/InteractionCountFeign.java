package com.wisdomhub.search.api;

import com.wisdomhub.search.api.dto.InteractionCountBatchRequest;
import com.wisdomhub.search.api.dto.InteractionCountBatchVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** interaction-service 的内部批量计数接口。 */
@FeignClient(name = "interaction-service", contextId = "searchInteractionCount",
        path = "/internal/interaction")
public interface InteractionCountFeign {

    @PostMapping("/counts")
    InteractionCountBatchVO counts(@RequestBody InteractionCountBatchRequest request);
}
