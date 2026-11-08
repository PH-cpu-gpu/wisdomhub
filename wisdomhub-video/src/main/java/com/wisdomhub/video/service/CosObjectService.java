package com.wisdomhub.video.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.wisdomhub.video.config.CosProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosObjectService {

    private final COSClient cosClient;
    private final CosProperties properties;

    /** 对象不存在返回 empty，其余异常交由调用方转成业务错误 */
    public Optional<ObjectStat> head(String objectKey) {
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(properties.getBucketName(), objectKey);
            return Optional.of(new ObjectStat(metadata.getContentLength(), metadata.getContentType()));
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** 尽力删除，失败只记日志 */
    public void deleteQuietly(String objectKey) {
        try {
            cosClient.deleteObject(properties.getBucketName(), objectKey);
            log.info("已删除 COS 对象, objectKey={}", objectKey);
        } catch (CosClientException e) {
            log.warn("删除 COS 对象失败, objectKey={}", objectKey, e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class ObjectStat {
        private final long contentLength;
        private final String contentType;
    }
}
