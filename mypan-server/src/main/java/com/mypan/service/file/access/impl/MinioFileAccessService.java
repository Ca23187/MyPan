package com.mypan.service.file.access.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileCategoryEnum;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.servlet.ContentTypeGuesser;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.dto.responseWrite.ObjMeta;
import com.mypan.service.dto.responseWrite.ReadPlan;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "minio"
)
public class MinioFileAccessService implements FileAccessService {

    private final FileInfoService fileInfoService;
    private final ObjectStorageService storageService;
    private final RedisComponent redisComponent;

    @Override
    public FileReadResourceDto openForRead(String fileIdOrSegment, String userId) {
        // 1) 一级缓存：命中缓存则绕开 DB / MinIO stat
        ReadPlan plan = redisComponent.getReadPlan(userId, fileIdOrSegment);

        // 2) miss：构建 + 写缓存
        if (plan == null) {
            plan = buildAndCacheMinioReadPlan(userId, fileIdOrSegment);
        }

        // 3) 用 plan 打开对象
        String objectKey = plan.getObjectKey();
        FileReadResourceDto res = new FileReadResourceDto();
        Long size = plan.getSize();
        res.setContentLength(size == null ? 0L : size);
        String ct = plan.getContentType();
        res.setContentType(StringUtils.hasText(ct) ? ct : ContentTypeGuesser.guess(objectKey));

        res.setOpenStream(() -> storageService.get(objectKey));
        res.setOpenRange((offset, length) -> storageService.getRange(objectKey, offset, length));
        return res;
    }

    private ObjMeta getObjMetaFromCache(String objectKey) {
        // 1) 正缓存
        ObjMeta meta = redisComponent.getObjMeta(objectKey);
        if (meta == null) {
            // 2) 负缓存
            if (redisComponent.isNegCached(objectKey)) {
                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            }
            // 3) stat 一次
            meta = storageService.statIfExists(objectKey);
            if (meta == null) {
                redisComponent.saveNeg(objectKey);
                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            }
            redisComponent.saveObjMeta(objectKey, meta);
        }
        return meta;
    }

    private FileReadResourceDto openResource(String objectKey) {
        ObjMeta meta = getObjMetaFromCache(objectKey);
        FileReadResourceDto res = new FileReadResourceDto();
        res.setContentLength(meta.getSize());
        String ct = meta.getContentType();
        res.setContentType(StringUtils.hasText(ct) ? ct : ContentTypeGuesser.guess(objectKey));
        res.setOpenStream(() -> storageService.get(objectKey));
        res.setOpenRange((offset, length) -> storageService.getRange(objectKey, offset, length));
        return res;
    }

    @Override
    public FileReadResourceDto openThumbnailForRead(String imageFolder, String imageName) {
        return openResource(imageFolder + "/" + imageName);
    }

    @Override
    public FileReadResourceDto openForDownload(String objectKey) {
        return openResource(objectKey);
    }

    private ReadPlan buildAndCacheMinioReadPlan(String userId, String fileIdOrSegment) {

        // ts：优先走 baseFolder 二级缓存（同 local）
        if (fileIdOrSegment.endsWith(".ts")) {
            int idx = fileIdOrSegment.indexOf('_');
            String realFileId = (idx > 0) ? fileIdOrSegment.substring(0, idx) : fileIdOrSegment;

            String baseFolder = redisComponent.getVideoBaseFolder(userId, realFileId);
            if (!StringUtils.hasText(baseFolder)) {
                // 负缓存命中直接返回不存在
                if (redisComponent.isNegCached(userId, realFileId)) {
                    throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
                }

                RLock lock = redisComponent.getVideoBaseFolderLock(userId, realFileId);
                boolean locked = false;
                try {
                    // 等最多 200ms 抢锁；不传 leaseTime，交给 watchdog 自动续期
                    locked = lock.tryLock(200, TimeUnit.MILLISECONDS);
                    if (locked) {
                        // 只有拿到锁的人负责查库
                        baseFolder = redisComponent.getVideoBaseFolder(userId, realFileId);
                        if (!StringUtils.hasText(baseFolder)) {
                            // 锁内再查一次负缓存，避免别的线程刚写入负缓存
                            if (redisComponent.isNegCached(userId, realFileId)) {
                                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
                            }
                            baseFolder = fileInfoService.resolveBaseFolderForTsByDb(realFileId, userId);
                            if (!StringUtils.hasText(baseFolder)) {
                                // 查不到时写负缓存
                                redisComponent.saveNeg(userId, realFileId);
                                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
                            }
                            redisComponent.saveVideoBaseFolder(userId, realFileId, baseFolder);
                        }
                    } else {
                        // 没拿到锁：只等缓存，不立刻查 DB
                        for (int i = 0; i < 3; i++) {
                            Thread.sleep(50);
                            baseFolder = redisComponent.getVideoBaseFolder(userId, realFileId);
                            if (StringUtils.hasText(baseFolder)) {
                                break;
                            }
                        }

                        // 别人可能已经确认不存在并写了负缓存
                        if (redisComponent.isNegCached(userId, realFileId)) {
                            throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
                        }

                        // 最终兜底
                        if (!StringUtils.hasText(baseFolder)) {
                            baseFolder = fileInfoService.resolveBaseFolderForTsByDb(realFileId, userId);
                            if (!StringUtils.hasText(baseFolder)) {
                                // 兜底查不到也写负缓存
                                redisComponent.saveNeg(userId, realFileId);
                                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
                            }
                            redisComponent.saveVideoBaseFolder(userId, realFileId, baseFolder);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("Obtaining video directory lock was interrupted");
                } finally {
                    if (locked && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            String objectKey = baseFolder + "/" + fileIdOrSegment;

            ReadPlan plan = new ReadPlan();
            plan.setObjectKey(objectKey);
            ObjMeta meta = storageService.statIfExists(objectKey);
            if (meta == null) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            plan.setSize(meta.getSize());
            plan.setContentType("video/mp2t");

            redisComponent.saveReadPlan(userId, fileIdOrSegment, plan, Constants.REDIS_TTL_READ_PLAN_TS);
            return plan;
        }

        // 非 ts：查 DB 拿 filePath + category，决定是普通文件还是 m3u8
        FileInfo fi = fileInfoService.findByFileIdAndUserIdAndDelFlag(
                fileIdOrSegment, userId, FileDelFlagEnum.ACTIVE.getFlag()
        );
        if (fi == null) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);

        String filePath = fi.getFilePath();
        if (!StringUtils.hasText(filePath)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);

        String objectKey;
        if (FileCategoryEnum.VIDEO.getCategory().equals(fi.getFileCategory())) {
            objectKey = StringTools.removeSuffix(filePath) + "/" + Constants.M3U8_NAME;
        } else {
            objectKey = filePath;
        }

        ReadPlan plan = new ReadPlan();
        plan.setObjectKey(objectKey);
        ObjMeta meta = storageService.statIfExists(objectKey);
        if (meta == null) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        plan.setSize(meta.getSize());
        String ct = meta.getContentType();
        plan.setContentType(StringUtils.hasText(ct)
                ? ct: ContentTypeGuesser.guess(objectKey));

        redisComponent.saveReadPlan(userId, fileIdOrSegment, plan, Constants.REDIS_TTL_READ_PLAN_NORM);
        return plan;
    }
}
