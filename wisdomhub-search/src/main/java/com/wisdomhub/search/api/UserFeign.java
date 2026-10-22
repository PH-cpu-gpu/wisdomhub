package com.wisdomhub.search.api;

import com.wisdomhub.search.api.dto.UserSyncDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * user-service 的内部读接口，供 user_index 对账取真值快照。
 */
@FeignClient(name = "user-service", contextId = "userSync", path = "/internal/user")
public interface UserFeign {

    /** since 用 ISO 字符串传；全量扫传 1970-01-01T00:00:00 */
    @GetMapping("/sync")
    List<UserSyncDTO> syncUsers(@RequestParam("since") String since,
                                @RequestParam("lastId") Long lastId,
                                @RequestParam("size") Integer size);
}
