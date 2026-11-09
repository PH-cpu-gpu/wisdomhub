package com.wisdomhub.video.constants;

public class VideoTagConstants {

    private VideoTagConstants() {
    }

    /** 与文章口径一致：一个视频最多 10 个标签 */
    public static final int MAX_TAGS_PER_VIDEO = 10;

    /** 标签字典的两级缓存 cacheName，最终 Redis key 形如 wisdomhub:video:cache:tag::all */
    public static final String CACHE_TAG = "tag";
}
