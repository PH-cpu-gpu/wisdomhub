package com.wisdomhub.user.service;

import com.wisdomhub.user.dto.RegisterDTO;

public interface UserService {

    void sendCodeByPhone(String phone);

    void sendCodeByEmail(String email);

    void register(RegisterDTO registerDTO);
}
