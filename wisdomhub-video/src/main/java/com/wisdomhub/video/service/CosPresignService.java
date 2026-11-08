package com.wisdomhub.video.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.video.config.CosProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosPresignService {

    private final COSClient cosClient;
    private final CosProperties properties;

    /** 生成预签名 PUT，签名覆盖 objectKey 与 Content-Type */
    public PresignedUpload presignPut(String objectKey, String mimeType, int ttlSeconds) {
        Date expiration = new Date(System.currentTimeMillis() + ttlSeconds * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucketName(), objectKey, HttpMethodName.PUT);
        request.setExpiration(expiration);
        request.setContentType(mimeType);

        try {
            URL url = cosClient.generatePresignedUrl(request);
            LocalDateTime expiresAt = LocalDateTime.ofInstant(expiration.toInstant(), ZoneOffset.UTC);
            return new PresignedUpload(url.toString(), expiresAt);
        } catch (CosClientException e) {
            log.error("生成 COS 上传凭证失败, objectKey={}", objectKey, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "上传凭证生成失败");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class PresignedUpload {
        private final String url;
        private final LocalDateTime expiresAt;
    }
}
