package com.wisdomhub.interaction.service;

public interface LikeService {

    int like(Integer targetType, Long targetId);

    int unlike(Integer targetType, Long targetId);
}
