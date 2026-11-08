package com.wisdomhub.video.constants;

import java.util.Locale;
import java.util.Map;

/** 允许直传的视频 MIME 与扩展名映射；扩展名由 MIME 决定，不看客户端文件名。 */
public final class VideoMimeTypes {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/webm", "webm",
            "video/x-matroska", "mkv");

    private VideoMimeTypes() {
    }

    public static String normalize(String mimeType) {
        return mimeType == null ? null : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowed(String mimeType) {
        return EXTENSIONS.containsKey(mimeType);
    }

    public static String extensionOf(String mimeType) {
        return EXTENSIONS.get(mimeType);
    }
}
