package com.wisdomhub.user.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.user.domain.dto.BindEmailDTO;
import com.wisdomhub.user.domain.dto.LoginDTO;
import com.wisdomhub.user.domain.dto.LogoutDTO;
import com.wisdomhub.user.domain.dto.ProfileDTO;
import com.wisdomhub.user.domain.dto.RegisterDTO;
import com.wisdomhub.user.domain.vo.LoginVO;
import com.wisdomhub.user.domain.vo.UserBriefVO;
import com.wisdomhub.user.domain.vo.UserProfileVO;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

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
}
