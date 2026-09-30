package com.wisdomhub.user.service;

import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.vo.LoginVO;

public interface UserService {

    void sendCodeByPhone(String phone);

    void sendCodeByEmail(String email);

    LoginVO register(RegisterDTO registerDTO);
}
