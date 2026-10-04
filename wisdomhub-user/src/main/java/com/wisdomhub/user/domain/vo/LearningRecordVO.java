package com.wisdomhub.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningRecordVO {

    /**
     * 技术名，对应图谱 Technology.name
     */
    private String technologyName;

    /**
     * 是否已掌握
     */
    private Boolean mastered;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
