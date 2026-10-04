package com.wisdomhub.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.user.domain.entity.UserFollow;
import com.wisdomhub.user.domain.vo.UserBriefVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 用户关注关系 Mapper 接口
 * </p>
 *
 * @author PulsarHaze
 * @since 2026-10-02
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /** 某人关注了谁, 按关注时间倒序; */
    IPage<UserBriefVO> selectFollowingPage(IPage<UserBriefVO> page, @Param("userId") Long userId);

    /** 谁关注了某人, 按关注时间倒序 */
    IPage<UserBriefVO> selectFansPage(IPage<UserBriefVO> page, @Param("userId") Long userId);
}
