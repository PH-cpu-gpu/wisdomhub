package com.wisdomhub.interaction.service;

public interface FavoriteService {

    int favorite(Integer targetType, Long targetId);

    int unfavorite(Integer targetType, Long targetId);
}
