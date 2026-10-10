package com.wisdomhub.user.util;

import cn.hutool.core.lang.UUID;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.user.config.CosPropertyConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CosUtil {

    /** 头像后缀白名单 */
    private static final Map<String, String> AVATAR_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp");

    private final COSClient cosClient;
    private final CosPropertyConfiguration properties;

    /**
     * 向COS头像桶中上传头像, 返回文件URL
     */
    public String uploadAvatar(MultipartFile file) {

        String filename = file.getOriginalFilename();

        String suffix = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT)
                : "";

        String contentType = AVATAR_CONTENT_TYPES.get(suffix);
        if (contentType == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "头像仅支持 jpg/jpeg/png/gif/webp 格式");
        }

        String objectKey = "avatar/" + UUID.randomUUID() + "." + suffix;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);

            PutObjectRequest request = new PutObjectRequest(
                    properties.getBucketName(),
                    objectKey,
                    file.getInputStream(),
                    metadata
            );

            // 设置头像公共读
            request.setCannedAcl(CannedAccessControlList.PublicRead);

            cosClient.putObject(request);

            return properties.getDomain() + "/" + objectKey;

        } catch (IOException e) {
            throw new RuntimeException("COS上传失败", e);
        }
    }

    /**
     * 删除对应url的头像
     */
    public void deleteByUrl(String url) {

        String prefix = properties.getDomain() + "/";
        if (!url.startsWith(prefix)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "非法的COS URL");
        }

        String objectKey = url.substring(prefix.length());
        cosClient.deleteObject(properties.getBucketName(), objectKey);
    }
}