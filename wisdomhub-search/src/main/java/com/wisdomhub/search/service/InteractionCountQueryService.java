package com.wisdomhub.search.service;

import com.wisdomhub.search.api.InteractionCountFeign;
import com.wisdomhub.search.api.dto.InteractionCountBatchRequest;
import com.wisdomhub.search.api.dto.InteractionCountBatchVO;
import com.wisdomhub.search.api.dto.InteractionCountQueryDTO;
import com.wisdomhub.search.api.dto.InteractionCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按 targetType 批量取 interaction 计数；interaction 内部接口上限 100 条/次。 */
@Service
@RequiredArgsConstructor
public class InteractionCountQueryService {

    private static final int BATCH_SIZE = 100;

    private final InteractionCountFeign interactionCountFeign;

    public InteractionCountVO get(int targetType, Long targetId) {
        if (targetId == null) {
            return null;
        }
        return byTargetIds(targetType, List.of(targetId)).get(targetId);
    }

    public Map<Long, InteractionCountVO> byTargetIds(int targetType, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = targetIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, InteractionCountVO> result = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<InteractionCountQueryDTO> items = ids
                    .subList(i, Math.min(i + BATCH_SIZE, ids.size()))
                    .stream()
                    .map(id -> {
                        InteractionCountQueryDTO item = new InteractionCountQueryDTO();
                        item.setTargetType(targetType);
                        item.setTargetId(id);
                        return item;
                    })
                    .toList();
            InteractionCountBatchRequest request = new InteractionCountBatchRequest();
            request.setItems(items);
            InteractionCountBatchVO response = interactionCountFeign.counts(request);
            if (response == null || response.getItems() == null) {
                continue;
            }
            for (InteractionCountVO vo : response.getItems()) {
                if (vo != null && vo.getTargetId() != null) {
                    result.put(vo.getTargetId(), vo);
                }
            }
        }
        return result;
    }
}
