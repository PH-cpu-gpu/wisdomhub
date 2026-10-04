package com.wisdomhub.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.user.constants.RedisKeyConstants;
import com.wisdomhub.user.domain.entity.User;
import com.wisdomhub.user.mapper.UserFollowMapper;
import com.wisdomhub.user.mapper.UserMapper;
import com.wisdomhub.user.service.FollowCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *   user_follow 的 COUNT(*)  权威源，永不写
 *   Redis 计数               热路径，关注/取关时 INCR / DECR
 *   user 表两列             持久化快照，只由本任务回写
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowCountSyncServiceImpl implements FollowCountSyncService {

    private static final int BATCH_SIZE = 500;

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String syncFollowCounts() {
        Map<Long, Long> trueFollowing = countMap(userFollowMapper.countGroupByFollower());
        Map<Long, Long> trueFans = countMap(userFollowMapper.countGroupByFollowee());
        List<Long> userIds = new ArrayList<>(collectUserIds(trueFollowing, trueFans));

        int redisFixed = 0;
        int columnFixed = 0;
        // 将大的用户集合分成小部分, 每次处理 BATCH_SIZE 个
        for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
            List<Long> batch = userIds.subList(i, Math.min(i + BATCH_SIZE, userIds.size()));
            redisFixed += correctRedis(batch, trueFollowing, trueFans);
            columnFixed += correctColumns(batch, trueFollowing, trueFans);
        }
        return "扫描用户 " + userIds.size() + " 个，校正 Redis " + redisFixed + " 处，回写 user 表 " + columnFixed + " 处";
    }

    /** 要处理的人：Redis 里有计数 key 的 ∪ 真值非 0 的。真值全 0 且 Redis 也没 key 的人没有可观测偏差 */
    private Set<Long> collectUserIds(Map<Long, Long> trueFollowing, Map<Long, Long> trueFans) {
        Set<Long> userIds = new HashSet<>();
        userIds.addAll(trueFollowing.keySet());
        userIds.addAll(trueFans.keySet());
        userIds.addAll(scanCountKeyUserIds(RedisKeyConstants.FOLLOW_COUNT_KEY));
        userIds.addAll(scanCountKeyUserIds(RedisKeyConstants.FANS_COUNT_KEY));
        return userIds;
    }

    /** SCAN 出某前缀下的全部 userId（禁用 KEYS）。Redis 丢过 key 时，靠上面两个真值集兜底 */
    private Set<Long> scanCountKeyUserIds(String prefix) {
        Set<Long> userIds = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(BATCH_SIZE).build())) {
            while (cursor.hasNext()) {
                String suffix = cursor.next().substring(prefix.length());
                try {
                    userIds.add(Long.valueOf(suffix));
                } catch (NumberFormatException e) {
                    // 前缀下的 key 只由本服务写，出现非数字只会是人工误写
                    log.warn("跳过非法的计数 key: {}", prefix + suffix);
                }
            }
        }
        return userIds;
    }

    private int correctRedis(List<Long> batch, Map<Long, Long> trueFollowing, Map<Long, Long> trueFans) {
        int fixed = 0;
        for (Long userId : batch) {
            if (alignRedis(RedisKeyConstants.FOLLOW_COUNT_KEY + userId, trueFollowing.getOrDefault(userId, 0L))) {
                fixed++;
            }
            if (alignRedis(RedisKeyConstants.FANS_COUNT_KEY + userId, trueFans.getOrDefault(userId, 0L))) {
                fixed++;
            }
        }
        return fixed;
    }

    /**
     * 值对了或 key 本来就不存在就不动 —— 后者是故意的：让读路径继续走"miss 则从 COUNT(*) 重建"，
     * 免得为一批零计数的用户长期养着 key
     */
    private boolean alignRedis(String key, long trueValue) {
        String current = stringRedisTemplate.opsForValue().get(key);
        if (current == null || current.equals(String.valueOf(trueValue))) {
            return false;
        }
        stringRedisTemplate.opsForValue().set(key, String.valueOf(trueValue));
        return true;
    }

    private int correctColumns(List<Long> batch, Map<Long, Long> trueFollowing, Map<Long, Long> trueFans) {
        int fixed = 0;
        for (User user : userMapper.selectByIds(batch)) {
            int following = Math.toIntExact(trueFollowing.getOrDefault(user.getId(), 0L));
            int fans = Math.toIntExact(trueFans.getOrDefault(user.getId(), 0L));
            if (Objects.equals(user.getFollowCount(), following) && Objects.equals(user.getFansCount(), fans)) {
                continue;
            }
            // 用 update(null, wrapper)：没有实体就不触发自动填充，update_time 不会被这次回写顶掉
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getFollowCount, following)
                    .set(User::getFansCount, fans)
                    .eq(User::getId, user.getId()));
            fixed++;
        }
        return fixed;
    }

    private static Map<Long, Long> countMap(List<Map<String, Object>> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object userId = row.get("userId");
            Object cnt = row.get("cnt");
            if (userId instanceof Number && cnt instanceof Number) {
                counts.put(((Number) userId).longValue(), ((Number) cnt).longValue());
            }
        }
        return counts;
    }
}
