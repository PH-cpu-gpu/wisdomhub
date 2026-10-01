package com.wisdomhub.user.service;

import com.wisdomhub.user.dto.LoginDTO;
import com.wisdomhub.user.dto.LoginOutDTO;
import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.vo.LoginVO;
import jakarta.validation.constraints.NotBlank;

public interface UserService {

    void sendCodeByPhone(String phone);

    void sendCodeByEmail(String email);

    LoginVO register(RegisterDTO registerDTO);

    LoginVO login(LoginDTO loginDTO);

    LoginVO refresh(String refreshToken);

    void loginOut(LoginOutDTO loginOutDTO);
}
