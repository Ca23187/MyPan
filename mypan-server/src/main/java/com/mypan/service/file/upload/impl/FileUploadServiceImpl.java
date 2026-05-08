package com.mypan.service.file.upload.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.*;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.servlet.ServletNetUtils;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.service.dto.UserSpaceDto;
import com.mypan.service.file.storage.ObjectStorageService;
import com.mypan.service.file.transcode.FileTranscodeService;
import com.mypan.service.file.upload.FileUploadService;
import com.mypan.service.user.UserInfoService;
import com.mypan.web.dto.request.UploadInitRequestDto;
import com.mypan.web.dto.response.upload.UploadSessionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.redisson.api.RLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final ObjectProvider<ObjectStorageService> objectStorageProvider;
    private final RedisUtils redisUtils;
    private final AppProperties appProperties;
    private final FileInfoRepository fileInfoRepository;
    private final UserInfoService userInfoService;
    private final RedisComponent redisComponent;
    private final FileTranscodeService fileTranscodeService;

    private ObjectStorageService oss() {  // 可选注入，切 minio 就启用
        return objectStorageProvider.getIfAvailable();
    }

    private boolean isMinioEnabled() {
        return oss() != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadSessionVo initUpload(String userId, UploadInitRequestDto req) {
        StringTools.FileNameValidator.validateSimpleName(req.getFileName(), true);

        String fileId = req.getFileId();
        if (!StringUtils.hasText(fileId)) {
            fileId = StringTools.getRandomString(Constants.RANDOM_FILE_ID_LENGTH);
        }

        UserSpaceDto spaceDto = redisComponent.getUserSpaceInfo(userId);

        // 1) 秒传判断
        UploadSessionVo instant = tryInstantUpload(
                userId, fileId, req.getFilePid(), req.getFileName(), req.getFileMd5(), spaceDto
        );
        if (instant != null) return instant;

        // 2) 配额粗校验（按整个文件大小）
        long fileSize = req.getFileSize() == null ? 0L : req.getFileSize();
        if (fileSize + spaceDto.getUsedSpace() > spaceDto.getTotalSpace()) {
            throw new BusinessException(ResponseCodeEnum.STORAGE_INSUFFICIENT);
        }

        UploadSessionVo vo = new UploadSessionVo();
        vo.setFileId(fileId);
        vo.setStatus(UploadStatusEnum.UPLOADING.getCode()); // "uploading"

        // 3) uploaded parts
        vo.setUploaded(resumeUpload(userId, fileId).getUploaded());

        // 4) minio: ensure uploadId exists
        if (isMinioEnabled()) {
            String uploadIdKey = redisComponent.mpuUploadIdKey(userId, fileId);
            String uploadId = redisUtils.get(uploadIdKey);  // 可能重复利用曾经中断的上传

            if (!StringUtils.hasText(uploadId)) {
                // 边界情况：先固定好 objectKey，防止跨月上传出错
                String objectKeyKey = redisComponent.mpuObjectKeyKey(userId, fileId);
                // 看看有没有之前中断的 objectKey
                String objectKey = redisUtils.get(objectKeyKey);
                if (StringUtils.hasText(objectKey)) {
                    redisUtils.expire(objectKeyKey, Constants.REDIS_TTL_MPU, Constants.REDIS_TIME_UNIT_MPU);
                } else {
                    String fileSuffix = StringTools.getSuffix(req.getFileName());
                    String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateTimePatternEnum.YYYYMM.getPattern()));
                    objectKey = month + "/" + userId + "/" + fileId + fileSuffix;
                    redisUtils.setEx(objectKeyKey, objectKey, Constants.REDIS_TTL_MPU, Constants.REDIS_TIME_UNIT_MPU);
                }
                uploadId = oss().initiateMultipartUpload(objectKey, null);
                redisUtils.setEx(
                        uploadIdKey,
                        uploadId,
                        Constants.REDIS_TTL_MPU,
                        Constants.REDIS_TIME_UNIT_MPU
                );
                // etagsKey 可能已存在（续传），这里不强删
            }
            vo.setUploadId(uploadId);
        }

        return vo;
    }

    @Override
    public UploadSessionVo resumeUpload(String userId, String fileId) {
        UploadSessionVo vo = new UploadSessionVo();
        vo.setFileId(fileId);

        // Minio
        if (isMinioEnabled()) return getMinioUploadStatus(userId, fileId, vo);

        // Local
        vo.setMpu(false);
        String currentUserFolderName = userId + fileId;
        Path tempFolder = Paths.get(
                appProperties.getProjectFolder(),
                Constants.FILE_FOLDER_TEMP,
                currentUserFolderName
        );

        if (!Files.exists(tempFolder) || !Files.isDirectory(tempFolder)) {
            vo.setUploaded(Collections.emptyList());
            return vo;
        }

        try (Stream<Path> s = Files.list(tempFolder)) {
            List<Integer> uploaded = s
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))   // 只认纯数字
                    .map(Integer::parseInt)
                    .distinct()
                    .sorted()
                    .toList();

            vo.setUploaded(uploaded);
            return vo;
        } catch (IOException e) {
            log.error("resumeUpload list tempFolder failed, userId={}, fileId={}, temp={}",
                    userId, fileId, tempFolder, e);
            vo.setUploaded(Collections.emptyList());
            return vo;
        }
    }

    private UploadSessionVo getMinioUploadStatus(String userId, String fileId, UploadSessionVo vo) {
        String etagsKey = redisComponent.mpuEtagsKey(userId, fileId);
        Map<String, String> etagMap = redisUtils.hgetAll(etagsKey);
        if (etagMap.isEmpty()) {
            vo.setUploaded(Collections.emptyList());
            vo.setMpu(true);
            return vo;
        }
        List<Integer> uploaded = etagMap.keySet().stream()
                .map(k -> {
                    int part = Integer.parseInt(k);
                    return part - 1; // partNumber -> chunkIndex
                })
                .distinct()
                .sorted()
                .toList();

        vo.setUploaded(uploaded);
        vo.setMpu(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UploadSessionVo uploadFile(String userId,
                                      String fileId,
                                      MultipartFile file,
                                      String fileName,
                                      String filePid,
                                      String fileMd5,
                                      Integer chunkIndex,
                                      Integer chunks) {
        if (chunkIndex >= chunks) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        StringTools.FileNameValidator.validateSimpleName(fileName, true);

        if (!StringUtils.hasText(fileId)) {
            fileId = StringTools.getRandomString(Constants.RANDOM_FILE_ID_LENGTH);
        }

        String fileSuffix = StringTools.getSuffix(fileName);
        FileTypeEnum fileTypeEnum = FileTypeEnum.getBySuffix(fileSuffix);

        try {
            UserSpaceDto spaceDto = redisComponent.getUserSpaceInfo(userId);

            // MinIO 分支
            if (isMinioEnabled()) {
                return handleMinioMultipartUpload(
                        userId, fileId, file, fileName, filePid, fileMd5,
                        chunkIndex, chunks, fileTypeEnum
                );
            }

            // Local 分支：临时目录
            String currentUserFolderName = userId + fileId;
            Path tempFileFolder = Paths.get(
                    appProperties.getProjectFolder(),
                    Constants.FILE_FOLDER_TEMP,
                    currentUserFolderName
            );

            // 1) 分片落盘
            saveChunkToTempFolder(file, chunkIndex, tempFileFolder);

            // 2) 更新临时 size 并校验配额
            Long total = redisComponent.recordUploadChunkSize(userId, fileId, chunkIndex, file.getSize());
            if (total == null) throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            if (spaceDto.getUsedSpace() + total > spaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.STORAGE_INSUFFICIENT);
            }

            // 3) 每一片都尝试推进到 “落库+合并”
            //    - 未齐：直接 uploading
            //    - 已齐但没抢到锁：说明别人会合并，直接 uploading
            UploadSessionVo vo = new UploadSessionVo();
            vo.setFileId(fileId);
            // 3) fast path：分片未齐，不进入落库+合并
            if (!redisComponent.isAllLocalChunksUploaded(userId, fileId, chunks)) {
                vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return vo;
            }

            // 4) 抢到锁的人负责落库 + afterCommit 合并/转码
            RLock mergeLock = redisComponent.getLocalMergeLock(userId, fileId);
            boolean mergeLocked = false;
            try {
                mergeLocked = mergeLock.tryLock(0, TimeUnit.SECONDS);
                if (!mergeLocked) {
                    vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                    return vo;
                }

                // 锁内再 double-check 一遍（防并发边界）
                if (!redisComponent.isAllLocalChunksUploaded(userId, fileId, chunks)) {
                    vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                    return vo;
                }

                Long totalSize = redisComponent.getUploadTotalSize(userId, fileId);
                if (totalSize == null || totalSize < 0) {
                    totalSize = 0L;
                }

                String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateTimePatternEnum.YYYYMM.getPattern()));
                String filePath = month + "/" + userId + "/" + fileId + fileSuffix;

                FileInfo fileInfo = new FileInfo();
                fileInfo.setFileId(fileId);
                fileInfo.setUserId(userId);
                fileInfo.setFileMd5(fileMd5);
                fileInfo.setFileName(autoRename(filePid, userId, fileName));
                fileInfo.setFilePath(filePath);
                fileInfo.setFilePid(filePid);
                fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
                fileInfo.setFileType(fileTypeEnum.getType());
                fileInfo.setFolderType(FileFolderTypeEnum.FILE.getType());
                fileInfo.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
                fileInfo.setStatus(FileStatusEnum.TRANSCODING.getStatus());
                fileInfo.setFileSize(totalSize);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        Path targetRoot = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE);
                        fileTranscodeService.transcodeFile(fileInfo, tempFileFolder, targetRoot);
                    }
                });
                fileInfoRepository.save(fileInfo);
                userInfoService.addUsedSpace(userId, totalSize);
                vo.setStatus(UploadStatusEnum.COMPLETED.getCode());
                redisComponent.clearTempUploadSize(userId, fileId);
                return vo;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Obtaining local merge lock was interrupted");
            } finally {
                if (mergeLocked && mergeLock.isHeldByCurrentThread()) {
                    mergeLock.unlock();
                }
            }
        } catch (Exception e) {
            if (ServletNetUtils.isClientAbort(e)) {
                // 用户暂停/取消导致连接断开：不算失败，不打 ERROR
                UploadSessionVo vo = new UploadSessionVo();
                vo.setFileId(fileId);
                vo.setStatus(UploadStatusEnum.UPLOADING.getCode()); // 维持上传中状态，让前端可继续
                return vo;
            }
            log.error("uploadFile failed, userId={}, fileId={}, chunkIndex={}", userId, fileId, chunkIndex, e);
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }


    @Override
    public UploadSessionVo abortUpload(String userId, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }

        // 1) 清临时统计（并行上传的 total/hash）
        redisComponent.clearTempUploadSize(userId, fileId);

        // 2) 清本地 tempFolder（不管是否 minio，都做兜底）
        try {
            Path tempFolder = Paths.get(appProperties.getProjectFolder(),
                    Constants.FILE_FOLDER_TEMP, userId + fileId);
            if (Files.exists(tempFolder)) {
                FileUtils.deleteDirectory(tempFolder.toFile());
            }
        } catch (Exception e) {
            log.warn("abortUpload cleanup local temp failed, userId={}, fileId={}", userId, fileId, e);
        }

        // 3) 如果是 minio：abort MPU + 清 mpu redis
        if (isMinioEnabled()) {
            String uploadIdKey = redisComponent.mpuUploadIdKey(userId, fileId);
            String objectKeyKey = redisComponent.mpuObjectKeyKey(userId, fileId);

            String uploadId = redisUtils.get(uploadIdKey);
            String objectKey = redisUtils.get(objectKeyKey);

            if (StringUtils.hasText(uploadId) && StringUtils.hasText(objectKey)) {
                try {
                    oss().abortMultipartUpload(objectKey, uploadId);
                } catch (Exception e) {
                    // abort 是尽力而为：失败也继续清 redis，避免卡死
                    log.warn("abortUpload abortMultipartUpload failed, userId={}, fileId={}, objectKey={}, uploadId={}",
                            userId, fileId, objectKey, uploadId, e);
                }
            }
            redisComponent.clearMpu(userId, fileId);
        }

        UploadSessionVo vo = new UploadSessionVo();
        vo.setFileId(fileId);
        vo.setStatus(UploadStatusEnum.ABORTED.getCode());
        vo.setUploaded(Collections.emptyList());
        return vo;
    }

    private UploadSessionVo tryInstantUpload(
            String userId, String fileId, String filePid, String fileName,
            String fileMd5, UserSpaceDto spaceDto) {

        FileInfo dbFile = fileInfoRepository
                .findFirstByFileMd5AndStatus(fileMd5, FileStatusEnum.ACTIVE.getStatus());
        if (dbFile == null) return null;

        if (dbFile.getFileSize() + spaceDto.getUsedSpace() > spaceDto.getTotalSpace())
            throw new BusinessException(ResponseCodeEnum.STORAGE_INSUFFICIENT);

        FileInfo newFile = new FileInfo();
        newFile.setFileId(fileId);
        newFile.setUserId(userId);
        newFile.setFilePid(filePid);
        newFile.setFileMd5(fileMd5);
        newFile.setFileSize(dbFile.getFileSize());
        newFile.setFilePath(dbFile.getFilePath());
        newFile.setFileCover(dbFile.getFileCover());
        newFile.setFileCategory(dbFile.getFileCategory());
        newFile.setFileType(dbFile.getFileType());
        newFile.setFolderType(dbFile.getFolderType());
        newFile.setStatus(FileStatusEnum.ACTIVE.getStatus());
        newFile.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
        newFile.setFileName(autoRename(filePid, userId, fileName));

        fileInfoRepository.save(newFile);
        userInfoService.addUsedSpace(userId, dbFile.getFileSize());

        // 清临时状态
        redisComponent.clearMpu(userId, fileId);
        redisComponent.clearTempUploadSize(userId, fileId);
        UploadSessionVo vo = new UploadSessionVo();
        vo.setFileId(fileId);
        vo.setStatus(UploadStatusEnum.INSTANT.getCode()); // "instant_upload"
        vo.setUploaded(Collections.emptyList());
        return vo;
    }

    private String autoRename(String filePid, String userId, String fileName) {
        // 查询是否有重名文件
        Set<String> occupied = fileInfoRepository
                .findFileNameByUserIdAndFilePidAndDelFlag(userId, filePid, FileDelFlagEnum.ACTIVE.getFlag());
        return StringTools.resolveConflict(fileName, occupied);
    }

    private UploadSessionVo handleMinioMultipartUpload(
            String userId, String fileId, MultipartFile file,
            String fileName, String filePid, String fileMd5,
            Integer chunkIndex, Integer chunks,
            FileTypeEnum fileTypeEnum) {

        ObjectStorageService os = oss();
        String uploadIdKey = redisComponent.mpuUploadIdKey(userId, fileId);
        String etagsKey = redisComponent.mpuEtagsKey(userId, fileId);
        String objectKeyKey = redisComponent.mpuObjectKeyKey(userId, fileId);

        String uploadId = null;
        String objectKey = null;

        try {
            // 1) 固定 objectKey（必须）
            objectKey = redisUtils.get(objectKeyKey);
            if (!StringUtils.hasText(objectKey)) {
                // 如果 init 没走到这里，说明前端流程不对：直接拒绝更安全
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }

            // 2) Redis 拿 uploadId
            uploadId = redisUtils.get(uploadIdKey);
            if (!StringUtils.hasText(uploadId)) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

            // 3) 上传 part
            int partNumber = chunkIndex + 1;
            try (InputStream in = file.getInputStream()) {
                String etag = os.uploadPart(objectKey, uploadId, partNumber, in, file.getSize());
                redisUtils.hset(etagsKey, String.valueOf(partNumber), etag);
                redisUtils.expire(etagsKey, Constants.REDIS_TTL_MPU, Constants.REDIS_TIME_UNIT_MPU);
                redisUtils.expire(uploadIdKey, Constants.REDIS_TTL_MPU, Constants.REDIS_TIME_UNIT_MPU);
                redisUtils.expire(objectKeyKey, Constants.REDIS_TTL_MPU, Constants.REDIS_TIME_UNIT_MPU);
            }

            // 4) 更新临时大小（配额控制）
            Long total = redisComponent.recordUploadChunkSize(userId, fileId, chunkIndex, file.getSize());
            if (total == null) throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);

            UploadSessionVo vo = new UploadSessionVo();
            vo.setFileId(fileId);

            // 5) fast path：parts 还没齐，不进入 complete
            Long partCount = redisUtils.hlen(etagsKey);
            if (partCount == null || partCount < chunks) {
                vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return vo;
            }

            // 6) 抢到锁的人负责 complete
            RLock completeLock = redisComponent.getMpuCompleteLock(userId, fileId);
            boolean completeLocked = false;
            try {
                completeLocked = completeLock.tryLock(0, TimeUnit.SECONDS);
                if (!completeLocked) {
                    vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                    return vo;
                }

                // 锁内严格校验，防止 hlen 误判或并发边界
                Map<String, String> etagMap = redisUtils.hgetAll(etagsKey);
                if (etagMap.size() != chunks) {
                    vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                    return vo;
                }

                for (int p = 1; p <= chunks; p++) {
                    if (!etagMap.containsKey(String.valueOf(p))) {
                        vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                        return vo;
                    }
                }

                List<ObjectStorageService.CompletedPart> parts = etagMap.entrySet().stream()
                        .map(e -> new ObjectStorageService.CompletedPart(
                                Integer.parseInt(e.getKey()), e.getValue()
                        ))
                        .toList();

                os.completeMultipartUpload(objectKey, uploadId, parts);

                // 7) 落库（路径存 objectKey）
                Long totalSize = redisComponent.getUploadTotalSize(userId, fileId);

                FileInfo fileInfo = new FileInfo();
                fileInfo.setFileId(fileId);
                fileInfo.setUserId(userId);
                fileInfo.setFileMd5(fileMd5);
                fileInfo.setFileName(autoRename(filePid, userId, fileName));
                fileInfo.setFilePath(objectKey);
                fileInfo.setFilePid(filePid);
                fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
                fileInfo.setFileType(fileTypeEnum.getType());
                fileInfo.setFolderType(FileFolderTypeEnum.FILE.getType());
                fileInfo.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
                fileInfo.setFileSize(totalSize);

                // 媒体：TRANSFER，非媒体：USING
                boolean isMedia = fileTypeEnum == FileTypeEnum.IMAGE
                        || fileTypeEnum == FileTypeEnum.VIDEO
                        || fileTypeEnum == FileTypeEnum.AUDIO;

                if (isMedia) {
                    fileInfo.setStatus(FileStatusEnum.TRANSCODING.getStatus());
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            Path targetRoot = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE);
                            fileTranscodeService.transcodeFile(fileInfo, null, targetRoot); // tempFolder=null 表示从 MinIO 拉取
                        }
                    });
                } else {
                    fileInfo.setStatus(FileStatusEnum.ACTIVE.getStatus());
                }
                fileInfoRepository.save(fileInfo);
                userInfoService.addUsedSpace(userId, totalSize);
                vo.setStatus(UploadStatusEnum.COMPLETED.getCode());
                redisComponent.clearMpu(userId, fileId);
                redisComponent.clearTempUploadSize(userId, fileId);
                return vo;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Obtaining MPU complete lock was interrupted");
            } finally {
                if (completeLocked && completeLock.isHeldByCurrentThread()) {
                    completeLock.unlock();
                }
            }
        } catch (Exception e) {
            if (ServletNetUtils.isClientAbort(e)) {
                // 客户端取消：不 abort，不删状态
                UploadSessionVo vo = new UploadSessionVo();
                vo.setFileId(fileId);
                vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return vo;
            }

            // 对“可恢复错误”：不 abort，不 delete（让用户重传该片）
            if (ServletNetUtils.isRecoverableMinioError(e)) {
                log.warn("mpu part upload recoverable error, userId={}, fileId={}, chunkIndex={}, err={}",
                        userId, fileId, chunkIndex, e.toString());
                UploadSessionVo vo = new UploadSessionVo();
                vo.setFileId(fileId);
                vo.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return vo;
            }

            // 不可恢复：abort 并清理，要求重新 init
            log.error("mpu upload fatal, userId={}, fileId={}, chunkIndex={}", userId, fileId, chunkIndex, e);
            if (StringUtils.hasText(objectKey) && StringUtils.hasText(uploadId))
                try { os.abortMultipartUpload(objectKey, uploadId); } catch (Exception ignore) {}
            redisComponent.clearMpu(userId, fileId);
            redisComponent.clearTempUploadSize(userId, fileId);

            if (e instanceof BusinessException be) throw be;
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    private void saveChunkToTempFolder(MultipartFile file, int chunkIndex, Path tempFileFolder) {
        Path chunkPath = tempFileFolder.resolve(String.valueOf(chunkIndex));
        Path tmp = chunkPath.resolveSibling(chunkPath.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(tempFileFolder);
            file.transferTo(tmp.toFile());
            try {
                Files.move(tmp, chunkPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, chunkPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.error("saveChunkToTempFolder failed, chunkIndex={}, tempFileFolder={}",
                    chunkIndex, tempFileFolder, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
