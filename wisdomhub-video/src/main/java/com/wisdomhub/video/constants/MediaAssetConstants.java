package com.wisdomhub.video.constants;

public class MediaAssetConstants {

    private MediaAssetConstants() {
    }

    /** 待上传：凭证已发，对象未确认 */
    public static final Integer UPLOADING = 1;
    /** 已上传：HEAD 校验通过 */
    public static final Integer UPLOADED = 2;
    /** 转码中 */
    public static final Integer TRANSCODING = 3;
    /** 失败：校验、探测或转码失败 */
    public static final Integer FAILED = 4;
    /** 审核中 */
    public static final Integer REVIEWING = 5;
    /** 审核拒绝 */
    public static final Integer REJECTED = 6;
    /** 就绪，可发布 */
    public static final Integer READY = 7;
}
