package com.wisdomhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdomhub.user.domain.entity.UserBrowseHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 浏览历史 Mapper 接口
 * </p>
 *
 * @author PulsarHaze
 * @since 2026-10-04
 */
@Mapper
public interface UserBrowseHistoryMapper extends BaseMapper<UserBrowseHistory> {

}
