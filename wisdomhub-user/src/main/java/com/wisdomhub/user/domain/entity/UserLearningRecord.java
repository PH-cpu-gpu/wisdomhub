package com.wisdomhub.user.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户学习记录
 * </p>
 *
 * @author PulsarHaze
 * @since 2026-10-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_learning_record")
public class UserLearningRecord implements Serializable {

    /**
     * 雪花 ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 技术名，对应图谱 Technology.name
     */
    private String technologyName;

    /**
     * 是否已掌握
     */
    private Boolean mastered;

    /**
     * 加入时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
