package com.wisdomhub.user.service;

import com.wisdomhub.common.core.result.PageResponse;
import java.util.List;
import com.wisdomhub.user.domain.dto.BindEmailDTO;
import com.wisdomhub.user.domain.dto.LoginDTO;
import com.wisdomhub.user.domain.dto.LogoutDTO;
import com.wisdomhub.user.domain.dto.ProfileDTO;
import com.wisdomhub.user.domain.dto.RegisterDTO;
import com.wisdomhub.user.domain.vo.LoginVO;
import com.wisdomhub.user.domain.vo.UserBriefVO;
import com.wisdomhub.user.domain.vo.UserProfileVO;
import com.wisdomhub.user.domain.vo.UserSyncVO;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

public interface UserService {

    void sendCodeByPhone(String phone);

    void sendCodeByEmail(String email);

    LoginVO register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    LoginVO refresh(String refreshToken);

    void logout(LogoutDTO logoutDTO);

    void updateProfile(ProfileDTO profileDTO);

    /** 绑定 / 改绑邮箱：要验证码、落库前转小写、被占用返回 409 */
    void bindEmail(BindEmailDTO bindEmailDTO);

    String updateAvatar(MultipartFile avatar);

    UserProfileVO getUserProfile(Long userId);

    void follow(@NotNull Long userId);

    void unfollow(Long followeeId);

    Boolean isFollowed(Long followeeId);

    PageResponse<UserBriefVO> listFollowing(Long userId, Integer page, Integer pageSize);

    PageResponse<UserBriefVO> listFans(Long userId, Integer page, Integer pageSize);

    /**
     * 按 ID 批量取用户摘要（用户名/头像/简介）。列表页要显示作者名，而文章接口只给 authorId
     * —— 跨服务 JOIN 被禁止（原则六），所以由这里批量补。
     * 查不到的 ID 直接不返回（用户被删），调用方自己兜底。
     */
    List<UserBriefVO> listBrief(List<Long> userIds);

    /** 供 user_index 对账的增量分页；since 为空按 id 全表扫，否则走 (update_time, id) 复合游标 */
    List<UserSyncVO> syncUsers(LocalDateTime since, Long lastId, Integer size);
}
