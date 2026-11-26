package com.wisdomhub.common.redis.message;

/**
 * 会话踢线消息：all=true 踢该用户全部连接，否则只踢 tokenHash 对应的连接。
 */
public record SessionKickMessage(Long userId, String tokenHash, boolean all) {
}