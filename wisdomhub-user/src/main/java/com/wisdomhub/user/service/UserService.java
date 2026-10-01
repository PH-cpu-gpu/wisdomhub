package com.wisdomhub.user.service;

import com.wisdomhub.user.dto.LoginDTO;
import com.wisdomhub.user.dto.LogoutDTO;
import com.wisdomhub.user.dto.ProfileDTO;
import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.vo.LoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    void sendCodeByPhone(String phone);

    void sendCodeByEmail(String email);

    LoginVO register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    LoginVO refresh(String refreshToken);

    void logout(LogoutDTO logoutDTO);

    void updateProfile(ProfileDTO profileDTO);

    String updateAvatar(MultipartFile avatar);
}
