package com.mypan.service.file.access.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileCategoryEnum;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.servlet.ContentTypeGuesser;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.dto.responseWrite.LimitedInputStream;
import com.mypan.service.dto.responseWrite.ReadPlan;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.db.FileInfoService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalFileAccessService implements FileAccessService {

    private final FileInfoService fileInfoService;

    private final AppProperties appProperties;

    private final RedisComponent redisComponent;

    @Override
    public FileReadResourceDto openForRead(String fileIdOrSegment, String userId) {
        if (!StringTools.isPathSegmentOk(fileIdOrSegment)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        // 1) 一级缓存：命中缓存则绕开 DB
        ReadPlan plan = redisComponent.getReadPlan(userId, fileIdOrSegment);

        // 2) miss：构建 + 写缓存
        if (plan == null) {
            plan = buildAndCacheLocalReadPlan(userId, fileIdOrSegment);
        }
        // 3) 用 plan 打开本地文件
        return openLocalPath(
                Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE, plan.getObjectKey()),
                plan.getSize(), plan.getContentType()
        );
    }

    @Override
    public FileReadResourceDto openThumbnailForRead(String imageFolder, String imageName) {
        if (!StringTools.isRelPathOk(imageFolder) || !StringTools.isPathSegmentOk(imageName)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        Path root = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE)
                .toAbsolutePath().normalize();
        Path path = root.resolve(imageFolder).resolve(imageName).normalize();

        if (!path.startsWith(root)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        return openLocalPath(path, null, null);
    }

    @Override
    public FileReadResourceDto openForDownload(String objectKey) {
        Path path = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE, objectKey);
        return openLocalPath(path, null, null);
    }

    private ReadPlan buildAndCacheLocalReadPlan(String userId, String fileIdOrSegment) {

        // ts：优先走 baseFolder 两级缓存
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

            String rel = baseFolder + "/" + fileIdOrSegment;

            Path abs = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE, rel);
            ReadPlan plan = new ReadPlan();
            plan.setObjectKey(rel);
            long size;
            try {
                size = Files.size(abs);
            } catch (NoSuchFileException e) {
                throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            } catch (Exception e) {
                size = 0L;
            }
            plan.setSize(size);
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

        String rel;
        if (FileCategoryEnum.VIDEO.getCategory().equals(fi.getFileCategory())) {
            rel = StringTools.removeSuffix(filePath) + "/" + Constants.M3U8_NAME;
        } else {
            rel = filePath; // 普通文件
        }
        ReadPlan plan = new ReadPlan();
        plan.setObjectKey(rel);
        long size;
        try { size = Files.size(
                Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE, rel));
        } catch (Exception e) {
            size = 0L;
        }
        plan.setSize(size);
        plan.setContentType(ContentTypeGuesser.guess(rel));

        redisComponent.saveReadPlan(userId, fileIdOrSegment, plan, Constants.REDIS_TTL_READ_PLAN_NORM);
        return plan;
    }

    private FileReadResourceDto openLocalPath(Path path, Long size, String contentType) {
        try {
            if (!Files.exists(path) || Files.isDirectory(path)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_READABLE);

            FileReadResourceDto res = new FileReadResourceDto();
            res.setContentLength(size == null ? Files.size(path) : size);
            res.setContentType(StringUtils.hasText(contentType)
                    ? contentType
                    : ContentTypeGuesser.guess(path.toString()));

            // 全量流
            res.setOpenStream(() -> {
                try {
                    return Files.newInputStream(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            // Range 流：RandomAccessFile seek
            res.setOpenRange((offset, length) -> {
                try {
                    RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
                    raf.seek(offset);
                    InputStream base = Channels.newInputStream(raf.getChannel());
                    return new LimitedInputStream(base, length, raf);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            return res;
        } catch (IOException e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }
}
