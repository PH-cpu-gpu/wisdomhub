package com.wisdomhub.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileVO {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户简介
     */
    private String bio;

    /**
     * 已发布文章数
     */
    private Integer articleCount;

    /**
     * 粉丝数
     */
    private Integer fansCount;

    /**
     * 关注数
     */
    private Integer followCount;
}
