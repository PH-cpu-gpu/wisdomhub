package com.wisdomhub.user.service.impl;

import java.util.Locale;
import  java.util.concurrent.TimeUnit;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.constants.RedisKeyConstants;
import com.wisdomhub.user.entity.User;
import com.wisdomhub.user.mapper.UserMapper;
import com.wisdomhub.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 根据手机号获取验证码
     */
    @Override
    public void sendCodeByPhone(String phone) {
        String key = RedisKeyConstants.CODE_BY_PHONE_KEY + phone;
        createCode(key);
    }

    /**
     * 根据邮箱获取验证码
     */
    @Override
    public void sendCodeByEmail(String email) {
        String lowerEmail = email.toLowerCase();
        String key = RedisKeyConstants.CODE_BY_EMAIL_KEY + lowerEmail;
        createCode(key);
    }

    /**
     * 注册接口
     */
    @Override
    public void register(RegisterDTO registerDTO) {
        String phone = registerDTO.getPhone();
        String originalEmail = registerDTO.getEmail();
        if (StrUtil.isEmpty(phone) && StrUtil.isEmpty(originalEmail)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        User user = User.builder()
                .password(passwordEncoder.encode(registerDTO.getPassword()))
                .username(registerDTO.getUsername())
                .build();
        if (!StrUtil.isEmpty(phone)) {
            String key = RedisKeyConstants.CODE_BY_PHONE_KEY + phone;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || !value.equals(registerDTO.getCode())) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码错误");
            }
            user.setPhone(phone);
        } else {
            String email = originalEmail.toLowerCase();
            String key = RedisKeyConstants.CODE_BY_EMAIL_KEY + email;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null || !value.equals(registerDTO.getCode())) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码错误");
            }
            user.setEmail(email);
        }
        userMapper.insert(user);
    }

    /**
     * 根据key创建验证码
     */
    private void createCode(String key) {
        // 先检验近期是否已经获取过验证码
        if (stringRedisTemplate.opsForValue().get(key) != null) {
            Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请等待" + expire + "秒后重试");
        }

        int code = ThreadLocalRandom.current().nextInt(100000, 1000000);
        // TODO 引入短信服务
        log.info("生成验证码为: {}", code);
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(code), 1, TimeUnit.MINUTES);
    }
}
