package com.wisdomhub.user.service.impl;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.hutool.core.date.DateField;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.util.JwtUtil;
import com.wisdomhub.user.config.JwtPropertyConfig;
import com.wisdomhub.user.constants.JwtPayloadConstants;
import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.constants.RedisKeyConstants;
import com.wisdomhub.user.entity.User;
import com.wisdomhub.user.mapper.UserMapper;
import com.wisdomhub.user.service.UserService;
import com.wisdomhub.user.vo.LoginVO;
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
    private final JwtPropertyConfig jwtPropertyConfig;

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
        String lowerEmail = email.toLowerCase(Locale.ROOT);
        String key = RedisKeyConstants.CODE_BY_EMAIL_KEY + lowerEmail;
        createCode(key);
    }

    /**
     * 注册接口
     */
    @Override
    public LoginVO register(RegisterDTO registerDTO) {
        // 检查电话注册or邮箱注册
        String phone = registerDTO.getPhone();
        String originalEmail = registerDTO.getEmail();
        if (StrUtil.isEmpty(phone) && StrUtil.isEmpty(originalEmail)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        User user = User.builder()
                .username(registerDTO.getUsername())
                .build();
        if (!StrUtil.isEmpty(phone)) {
            user.setPhone(phone);
        } else {
            user.setEmail(originalEmail.toLowerCase(Locale.ROOT));
        }

        // 预检只为说清是哪个字段被占用; 并发下两个请求可能同时通过, 最终仍由唯一索引兜底
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户名已被占用");
        }
        if (user.getPhone() != null
                && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getPhone, user.getPhone()))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "手机号已被占用");
        }
        if (user.getEmail() != null
                && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, user.getEmail()))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "邮箱已被占用");
        }

        // 检查验证码是否正确
        String key = user.getPhone() != null
                ? RedisKeyConstants.CODE_BY_PHONE_KEY + user.getPhone()
                : RedisKeyConstants.CODE_BY_EMAIL_KEY + user.getEmail();
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (!value.equals(registerDTO.getCode())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码错误");
        }

        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        userMapper.insert(user);
        stringRedisTemplate.delete(key);

        // 登录成功, 生成双token并返回
        Long userId = user.getId();
        String accessToken = JwtUtil.createToken(Map.of(JwtPayloadConstants.USER_ID, userId),
                jwtPropertyConfig.privateKey(),
                (int) RedisTtl.ACCESS_TOKEN.toMinutes(),
                DateField.MINUTE);
        String refreshToken = UUID.randomUUID().toString();
        String refreshKey = RedisKeyConstants.REFRESH_TOKEN_KEY + userId;
        stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, RedisTtl.REFRESH_TOKEN);
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * 根据key创建验证码
     */
    private void createCode(String key) {
        int code = ThreadLocalRandom.current().nextInt(100000, 1000000);
        // 这里使用setIfAbsent, 保证原子性
        Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(code), RedisTtl.CAPTCHA);

        if (!Boolean.TRUE.equals(created)) {
            Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            // key 恰好在此期间过期时 getExpire 返回 -2, 拿不到剩余时间就退回错误码自带的文案
            if (expire > 0) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "请等待" + expire + "秒后重试");
            }
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }

        // TODO 引入短信服务
        log.info("生成验证码为: {}", code);
    }
}
