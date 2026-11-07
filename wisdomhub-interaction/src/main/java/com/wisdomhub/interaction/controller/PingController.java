package com.wisdomhub.interaction.controller;

import com.wisdomhub.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "冒烟", description = "阶段 4 联调用，后续可删")
@RestController
public class PingController {

    @Operation(summary = "连通性探测")
    @GetMapping("/api/interaction/ping")
    public R<String> ping() {
        return R.success("pong");
    }
}
