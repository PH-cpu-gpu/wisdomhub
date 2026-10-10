package com.wisdomhub.user.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.common.redis.constants.RedisSharedConstants;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.security.constants.SecurityConstants;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.security.util.JwtUtil;
import com.wisdomhub.user.api.ArticleFeign;
import com.wisdomhub.user.config.JwtPropertyConfiguration;
import com.wisdomhub.user.constants.JwtPayloadConstants;
import com.wisdomhub.user.domain.dto.*;
import com.wisdomhub.user.constants.RedisKeyConstants;
import com.wisdomhub.user.domain.entity.User;
import com.wisdomhub.user.domain.entity.UserFollow;
import com.wisdomhub.user.domain.vo.UserBriefVO;
import com.wisdomhub.user.domain.vo.UserProfileVO;
import com.wisdomhub.user.mapper.UserFollowMapper;
import com.wisdomhub.user.mapper.UserMapper;
import com.wisdomhub.user.service.UserService;
import com.wisdomhub.user.util.CosUtil;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.user.domain.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ArticleFeign articleFeign;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtPropertyConfiguration jwtPropertyConfiguration;
    private final CosUtil cosUtil;

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
        TokenDTO tokenDTO = createTokenByUserId(user.getId());
        return LoginVO.builder()
                .accessToken(tokenDTO.getAccessToken())
                .refreshToken(tokenDTO.getRefreshToken())
                .hasEmail(user.getEmail() != null)
                .build();
    }

    /**
     * 根据手机号或邮箱以及密码登录
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        // 检查电话登录or邮箱登录
        String phone = loginDTO.getPhone();
        String originalEmail = loginDTO.getEmail();
        if (StrUtil.isEmpty(phone) && StrUtil.isEmpty(originalEmail)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        LambdaQueryWrapper<User> wrapper;
        if (!StrUtil.isEmpty(phone)) {
            wrapper = new LambdaQueryWrapper<User>().eq(User::getPhone, phone);
        } else {
            wrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, originalEmail.toLowerCase(Locale.ROOT));
        }

        // 查询用户是否存在
        User user = userMapper.selectOne(wrapper);

        // 检查密码是否正确
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "账号或密码错误");
        }

        // 登录成功, 返回双token
        TokenDTO tokenDTO = createTokenByUserId(user.getId());
        return LoginVO.builder()
                .accessToken(tokenDTO.getAccessToken())
                .refreshToken(tokenDTO.getRefreshToken())
                .hasEmail(user.getEmail() != null)
                .build();
    }

    /**
     * 刷新token
     */
    @Override
    public LoginVO refresh(String refreshToken) {
        String key = RedisKeyConstants.REFRESH_TOKEN_BY_TOKEN_KEY + refreshToken;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "refreshToken无效或已过期");
        }
        // refreshToken有效
        Long userId = Long.parseLong(value);
        TokenDTO tokenDTO = createTokenByUserId(userId);
        stringRedisTemplate.delete(key);
        return LoginVO.builder()
                .accessToken(tokenDTO.getAccessToken())
                .refreshToken(tokenDTO.getRefreshToken())
                .build();
    }

    /**
     * 注销
     */
    @Override
    public void logout(LogoutDTO logoutDTO) {
        String accessToken = logoutDTO.getAccessToken();
        String refreshToken = logoutDTO.getRefreshToken();

        // 获取用户id
        Long userId = UserContext.getUserId();

        // 删除refreshToken
        stringRedisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN_BY_TOKEN_KEY + refreshToken);
        stringRedisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN_BY_USER_KEY + userId);

        // 将accessToken加入redis黑名单
        // 因为accessToken本身较长, 这里做Hash来减少长度, 从而节约redis的内存
        String rawToken = StrUtil.removePrefix(accessToken, SecurityConstants.TOKEN_PREFIX);
        String blacklistKey = RedisSharedConstants.BLACKLIST_KEY + DigestUtil.sha256Hex(rawToken);
        stringRedisTemplate.opsForValue().set(blacklistKey, "1", RedisTtl.ACCESS_TOKEN);
    }

    /**
     * 更新用户资料
     */
    @Override
    public void updateProfile(ProfileDTO profileDTO) {
        Long userId = UserContext.getUserId();
        String username = profileDTO.getUsername();

        // 预检用户名是否被别人占用
        if (StrUtil.isNotBlank(username)
                && userMapper.exists(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .ne(User::getId, userId))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户名已被占用");
        }

        User user = User.builder()
                .id(userId)
                .username(username)
                .bio(profileDTO.getBio())
                .build();
        userMapper.updateById(user);
    }

    /**
     * 绑定 / 改绑邮箱。先校验证码再查占用 —— 反过来会让「邮箱是否已被注册」变成一个
     * 不需要验证码就能探测的接口（用户枚举）
     */
    @Override
    public void bindEmail(BindEmailDTO bindEmailDTO) {
        Long userId = UserContext.getUserId();
        String email = bindEmailDTO.getEmail().toLowerCase(Locale.ROOT);

        String key = RedisKeyConstants.CODE_BY_EMAIL_KEY + email;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码不存在或已过期");
        }
        if (!value.equals(bindEmailDTO.getCode())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "验证码错误");
        }

        // 预检只为说清是谁占用了; 并发下最终仍由 uk_email 兜底
        if (userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(User::getId, userId))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "邮箱已被占用");
        }

        userMapper.updateById(User.builder().id(userId).email(email).build());
        stringRedisTemplate.delete(key);
    }

    /** 一次最多问 100 个 ID —— 只是防滥用，列表页去重后远小于这个数 */
    private static final int MAX_BRIEF_IDS = 100;

    @Override
    public List<UserBriefVO> listBrief(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().distinct().limit(MAX_BRIEF_IDS).toList();
        return userMapper.selectByIds(ids).stream()
                .map(user -> UserBriefVO.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .avatar(user.getAvatar())
                        .bio(user.getBio())
                        .build())
                .toList();
    }

    /**
     * 更新用户头像
     */
    @Override
    public String updateAvatar(MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "头像文件不能为空");
        }
        Long userId = UserContext.getUserId();

        User old = userMapper.selectById(userId);
        String oldUrl = old == null ? null : old.getAvatar();

        String newUrl = cosUtil.uploadAvatar(avatar);
        userMapper.updateById(User.builder().id(userId).avatar(newUrl).build());

        // 先落库再删旧文件
        if (StrUtil.isNotBlank(oldUrl)) {
            cosUtil.deleteByUrl(oldUrl);
        }
        return newUrl;
    }

    /**
     * 查询用户简介
     */
    @Override
    public UserProfileVO getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        // 计数优先读 Redis, 不取 user 表的快照列
        UserProfileVO userProfile = BeanUtil.copyProperties(user, UserProfileVO.class, "followCount", "fansCount");
        userProfile.setUserId(userId);
        try {
            userProfile.setFollowCount(ensureCount(RedisKeyConstants.FOLLOW_COUNT_KEY + userId,
                    () -> userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, userId))));
            userProfile.setFansCount(ensureCount(RedisKeyConstants.FANS_COUNT_KEY + userId,
                    () -> userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFolloweeId, userId))));
        } catch (Exception e) {
            // Redis 不可用: 退回 user 表快照列
            log.warn("计数读取失败, 退回 user 表快照: userId={}", userId, e);
            userProfile.setFollowCount(user.getFollowCount());
            userProfile.setFansCount(user.getFansCount());
        }
        Long articleCount = articleFeign.countArticle(userId);
        userProfile.setArticleCount(Math.toIntExact(articleCount));
        return userProfile;
    }

    /**
     * 关注用户
     */
    @Override
    public void follow(Long followeeId) {
        Long followerId = UserContext.getUserId();
        if (followerId.equals(followeeId)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "不能关注自己");
        }
        String followKey = RedisKeyConstants.FOLLOW_COUNT_KEY + followerId;
        String fansKey = RedisKeyConstants.FANS_COUNT_KEY + followeeId;
        // 先确保两个key存在
        ensureCount(followKey, () -> userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, followerId)));
        ensureCount(fansKey, () -> userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFolloweeId, followeeId)));
        // 向数据库中插入数据
        try {
            userFollowMapper.insert(UserFollow.builder().followerId(followerId).followeeId(followeeId).build());
        } catch (DuplicateKeyException e) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(followKey);
        stringRedisTemplate.opsForValue().increment(fansKey);
    }

    /**
     * 确保关注数/粉丝数 key 存在, 不存在则从 COUNT(*) 重建, 返回当前计数
     */
    private int ensureCount(String key, Supplier<Long> dbCount) {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        int count = Math.toIntExact(dbCount.get());
        // SETNX 回填, 并发 INCR 已经建了 key 时不能覆盖它
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(count));
        return count;
    }

    /**
     * 取关
     */
    @Override
    public void unfollow(Long followeeId) {
        Long followerId = CurrentUserUtil.id();
        if (followeeId.equals(followerId)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "不能取关自己");
        }
        String followKey = RedisKeyConstants.FOLLOW_COUNT_KEY + followerId;
        String fansKey = RedisKeyConstants.FANS_COUNT_KEY + followeeId;
        // 先确保两个key存在, 此时 COUNT(*) 仍包含待删的这条
        ensureCount(followKey, () -> userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, followerId)));
        ensureCount(fansKey, () -> userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFolloweeId, followeeId)));
        // 先删库, 确实删到行才减计数
        int deleted = userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (deleted == 0) {
            return;
        }
        stringRedisTemplate.opsForValue().decrement(followKey);
        stringRedisTemplate.opsForValue().decrement(fansKey);
    }

    /**
     * 当前登录用户是否已关注某人
     */
    @Override
    public Boolean isFollowed(Long followeeId) {
        Long followerId = CurrentUserUtil.id();
        return userFollowMapper.exists(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
    }

    /**
     * 某人的关注列表
     */
    @Override
    public PageResponse<UserBriefVO> listFollowing(Long userId, Integer page, Integer pageSize) {
        IPage<UserBriefVO> result = userFollowMapper.selectFollowingPage(PageUtil.of(page, pageSize), userId);
        return PageUtil.toResponse(result);
    }

    /**
     * 某人的粉丝列表
     */
    @Override
    public PageResponse<UserBriefVO> listFans(Long userId, Integer page, Integer pageSize) {
        IPage<UserBriefVO> result = userFollowMapper.selectFansPage(PageUtil.of(page, pageSize), userId);
        return PageUtil.toResponse(result);
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

    /**
     * 根据userId获取用户登录双token
     */
    private TokenDTO createTokenByUserId(Long userId) {
        String accessToken = JwtUtil.createToken(Map.of(JwtPayloadConstants.USER_ID, userId),
                jwtPropertyConfiguration.privateKey(),
                (int) RedisTtl.ACCESS_TOKEN.toMinutes(),
                DateField.MINUTE);
        String refreshToken = UUID.randomUUID().toString();
        String refreshUserKey = RedisKeyConstants.REFRESH_TOKEN_BY_USER_KEY + userId;
        String refreshTokenKey = RedisKeyConstants.REFRESH_TOKEN_BY_TOKEN_KEY + refreshToken;
        stringRedisTemplate.opsForValue().set(refreshUserKey, refreshToken, RedisTtl.REFRESH_TOKEN);
        stringRedisTemplate.opsForValue().set(refreshTokenKey, String.valueOf(userId), RedisTtl.REFRESH_TOKEN);
        return TokenDTO.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }
}
