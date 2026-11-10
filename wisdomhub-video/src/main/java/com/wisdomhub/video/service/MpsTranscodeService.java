package com.wisdomhub.video.service;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.mps.v20190612.MpsClient;
import com.tencentcloudapi.mps.v20190612.models.CosInputInfo;
import com.tencentcloudapi.mps.v20190612.models.CosOutputStorage;
import com.tencentcloudapi.mps.v20190612.models.DescribeMediaMetaDataRequest;
import com.tencentcloudapi.mps.v20190612.models.DescribeMediaMetaDataResponse;
import com.tencentcloudapi.mps.v20190612.models.DescribeTaskDetailRequest;
import com.tencentcloudapi.mps.v20190612.models.DescribeTaskDetailResponse;
import com.tencentcloudapi.mps.v20190612.models.MediaMetaData;
import com.tencentcloudapi.mps.v20190612.models.MediaInputInfo;
import com.tencentcloudapi.mps.v20190612.models.MediaProcessTaskInput;
import com.tencentcloudapi.mps.v20190612.models.MediaProcessTaskResult;
import com.tencentcloudapi.mps.v20190612.models.MediaProcessTaskSnapshotByTimeOffsetResult;
import com.tencentcloudapi.mps.v20190612.models.MediaProcessTaskTranscodeResult;
import com.tencentcloudapi.mps.v20190612.models.MediaTranscodeItem;
import com.tencentcloudapi.mps.v20190612.models.ProcessMediaRequest;
import com.tencentcloudapi.mps.v20190612.models.ProcessMediaResponse;
import com.tencentcloudapi.mps.v20190612.models.SnapshotByTimeOffsetTaskInput;
import com.tencentcloudapi.mps.v20190612.models.TaskOutputStorage;
import com.tencentcloudapi.mps.v20190612.models.TranscodeTaskInput;
import com.tencentcloudapi.mps.v20190612.models.WorkflowTask;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.video.config.CosProperties;
import com.wisdomhub.video.config.MpsTaskProperties;
import com.wisdomhub.video.domain.MpsTaskResult;
import com.wisdomhub.video.domain.entity.MediaAsset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpsTranscodeService {

    private static final String TYPE_COS = "COS";
    private static final String STATUS_FINISH = "FINISH";
    private static final String STATUS_FAIL = "FAIL";
    private static final int FAIL_REASON_MAX_LENGTH = 255;

    private final MpsClient mpsClient;
    private final CosProperties cosProperties;
    private final MpsTaskProperties taskProperties;

    /** 提交转码 + 截图任务，返回 MPS TaskId */
    public String submit(MediaAsset asset) {
        ProcessMediaRequest request = new ProcessMediaRequest();
        request.setInputInfo(buildInputInfo(asset));
        request.setOutputStorage(buildOutputStorage());
        request.setOutputDir(outputDir(asset));
        request.setMediaProcessTask(buildProcessTask(asset));
        request.setSessionId("video-transcode-" + asset.getId());
        request.setSessionContext("{\"assetId\":" + asset.getId() + "}");

        try {
            ProcessMediaResponse response = mpsClient.ProcessMedia(request);
            log.info("MPS 转码任务已提交, assetId={}, taskId={}", asset.getId(), response.getTaskId());
            return response.getTaskId();
        } catch (TencentCloudSDKException e) {
            log.error("MPS 转码任务提交失败, assetId={}, code={}, message={}",
                    asset.getId(), e.getErrorCode(), e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "转码任务提交失败");
        }
    }

    /** 转码前探测时长；探测失败返回 null，由转码后兜底。 */
    public Long probeDurationMs(MediaAsset asset) {
        DescribeMediaMetaDataRequest request = new DescribeMediaMetaDataRequest();
        request.setInputInfo(buildInputInfo(asset));
        try {
            DescribeMediaMetaDataResponse response = mpsClient.DescribeMediaMetaData(request);
            MediaMetaData metadata = response.getMetaData();
            if (metadata == null || metadata.getDuration() == null) {
                return null;
            }
            return (long) Math.round(metadata.getDuration() * 1000);
        } catch (Exception e) {
            log.warn("MPS 媒体信息探测失败，转码后再校验时长, assetId={}, message={}",
                    asset.getId(), e.getMessage());
            return null;
        }
    }

    /** 查询任务详情；未结束时 finished=false */
    public MpsTaskResult describe(String taskId) {
        DescribeTaskDetailRequest request = new DescribeTaskDetailRequest();
        request.setTaskId(taskId);
        try {
            DescribeTaskDetailResponse response = mpsClient.DescribeTaskDetail(request);
            if (!STATUS_FINISH.equals(response.getStatus())) {
                return MpsTaskResult.builder().finished(false).build();
            }
            WorkflowTask workflowTask = response.getWorkflowTask();
            if (workflowTask == null) {
                return fail("MPS 任务缺少工作流结果");
            }
            if (workflowTask.getErrCode() != null && workflowTask.getErrCode() != 0) {
                return fail("MPS 任务失败：" + safe(workflowTask.getMessage())
                        + " (" + workflowTask.getErrCode() + ")");
            }
            return parseResults(workflowTask);
        } catch (TencentCloudSDKException e) {
            log.error("查询 MPS 任务失败, taskId={}, code={}, message={}",
                    taskId, e.getErrorCode(), e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "转码任务查询失败");
        }
    }

    private MediaInputInfo buildInputInfo(MediaAsset asset) {
        CosInputInfo cosInputInfo = new CosInputInfo();
        cosInputInfo.setBucket(cosProperties.getBucketName());
        cosInputInfo.setRegion(cosProperties.getRegion());
        cosInputInfo.setObject(asset.getObjectKey());

        MediaInputInfo inputInfo = new MediaInputInfo();
        inputInfo.setType(TYPE_COS);
        inputInfo.setCosInputInfo(cosInputInfo);
        return inputInfo;
    }

    private TaskOutputStorage buildOutputStorage() {
        CosOutputStorage cosOutputStorage = new CosOutputStorage();
        cosOutputStorage.setBucket(cosProperties.getBucketName());
        cosOutputStorage.setRegion(cosProperties.getRegion());

        TaskOutputStorage outputStorage = new TaskOutputStorage();
        outputStorage.setType(TYPE_COS);
        outputStorage.setCosOutputStorage(cosOutputStorage);
        return outputStorage;
    }

    private String outputDir(MediaAsset asset) {
        return "/hls/" + asset.getOwnerId() + "/" + asset.getId() + "/";
    }

    private MediaProcessTaskInput buildProcessTask(MediaAsset asset) {
        TranscodeTaskInput transcode = new TranscodeTaskInput();
        transcode.setDefinition(taskProperties.getTranscodeTemplateId());
        transcode.setOutputObjectPath("index.m3u8");
        transcode.setSegmentObjectName("seg-{number}.{format}");

        SnapshotByTimeOffsetTaskInput snapshot = new SnapshotByTimeOffsetTaskInput();
        snapshot.setDefinition(taskProperties.getSnapshotTemplateId());
        snapshot.setExtTimeOffsetSet(new String[]{taskProperties.getSnapshotTimeOffsetSeconds() + "s"});
        snapshot.setOutputObjectPath("/video/" + asset.getOwnerId() + "/cover/" + asset.getId() + ".{format}");

        MediaProcessTaskInput task = new MediaProcessTaskInput();
        task.setTranscodeTaskSet(new TranscodeTaskInput[]{transcode});
        task.setSnapshotByTimeOffsetTaskSet(new SnapshotByTimeOffsetTaskInput[]{snapshot});
        return task;
    }

    private MpsTaskResult parseResults(WorkflowTask workflowTask) {
        Long durationMs = null;
        Integer width = null;
        Integer height = null;
        String playlistKey = null;
        String coverKey = null;
        String failReason = null;

        MediaProcessTaskResult[] results = workflowTask.getMediaProcessResultSet();
        if (results != null) {
            for (MediaProcessTaskResult result : results) {
                if (result.getTranscodeTask() != null) {
                    MediaProcessTaskTranscodeResult transcode = result.getTranscodeTask();
                    if (isFailed(transcode.getErrCode(), transcode.getStatus())) {
                        failReason = "转码失败：" + safe(transcode.getMessage())
                                + " (" + safe(transcode.getErrCodeExt()) + ")";
                        continue;
                    }
                    MediaTranscodeItem output = transcode.getOutput();
                    if (output != null) {
                        durationMs = output.getDuration() == null
                                ? null : Long.valueOf(Math.round(output.getDuration() * 1000));
                        width = output.getWidth() == null ? null : output.getWidth().intValue();
                        height = output.getHeight() == null ? null : output.getHeight().intValue();
                        playlistKey = stripLeadingSlash(output.getPath());
                    }
                }
                if (result.getSnapshotByTimeOffsetTask() != null) {
                    MediaProcessTaskSnapshotByTimeOffsetResult snapshot = result.getSnapshotByTimeOffsetTask();
                    if (isFailed(snapshot.getErrCode(), snapshot.getStatus())) {
                        log.warn("MPS 截图失败, errCode={}, message={}",
                                snapshot.getErrCode(), snapshot.getMessage());
                    } else if (snapshot.getOutput() != null
                            && snapshot.getOutput().getPicInfoSet() != null
                            && snapshot.getOutput().getPicInfoSet().length > 0) {
                        coverKey = stripLeadingSlash(snapshot.getOutput().getPicInfoSet()[0].getPath());
                    }
                }
            }
        }

        if (failReason != null) {
            return fail(failReason);
        }
        if (playlistKey == null) {
            return fail("MPS 任务完成但缺少转码产物");
        }
        return MpsTaskResult.builder()
                .finished(true)
                .success(true)
                .durationMs(durationMs)
                .width(width)
                .height(height)
                .playlistKey(playlistKey)
                .coverKey(coverKey)
                .build();
    }

    private boolean isFailed(Long errCode, String status) {
        return (errCode != null && errCode != 0) || STATUS_FAIL.equalsIgnoreCase(status);
    }

    private MpsTaskResult fail(String reason) {
        return MpsTaskResult.builder()
                .finished(true)
                .success(false)
                .failReason(truncate(reason))
                .build();
    }

    private String stripLeadingSlash(String path) {
        if (path == null) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        if (value == null) {
            return "转码失败";
        }
        return value.length() > FAIL_REASON_MAX_LENGTH
                ? value.substring(0, FAIL_REASON_MAX_LENGTH)
                : value;
    }
}
