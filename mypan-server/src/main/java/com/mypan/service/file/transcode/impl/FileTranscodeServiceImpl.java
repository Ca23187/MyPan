package com.mypan.service.file.transcode.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileStatusEnum;
import com.mypan.common.enums.FileTypeEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.file.AudioTools;
import com.mypan.common.utils.file.FileTools;
import com.mypan.common.utils.file.ThumbnailTools;
import com.mypan.common.utils.file.VideoTools;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.storage.ObjectStorageService;
import com.mypan.service.file.transcode.FileTranscodeService;
import com.mypan.service.schedule.impl.FileCleanServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileTranscodeServiceImpl implements FileTranscodeService {

    private final FileInfoService fileInfoService;
    private final ObjectProvider<ObjectStorageService> objectStorageProvider;
    private final FileCleanServiceImpl fileCleanService;

    private ObjectStorageService oss() {  // 可选注入，切 minio 就启用
        return objectStorageProvider.getIfAvailable();
    }

    private boolean isMinioEnabled() {
        return oss() != null;
    }

    @Override
    @Async
    public void transcodeFile(FileInfo fileInfo, Path tempFolder, Path targetRoot) {
        if (fileInfo == null) return;

        // 重新读最新状态（避免重复处理）
        FileInfo latest = fileInfoService.findByFileIdAndUserId(fileInfo.getFileId(), fileInfo.getUserId());
        if (latest == null) {
            // 没记录了，清理本地 temp
            try {
                if (tempFolder != null && Files.exists(tempFolder)) {
                    FileUtils.deleteDirectory(tempFolder.toFile());
                }
            } catch (Exception e) {
                log.warn("delete temp folder failed: {}", tempFolder, e);
            }
            return;
        }

        if (!FileStatusEnum.TRANSCODING.getStatus().equals(latest.getStatus())) return;

        boolean ok = true;
        Path localInput = null;     // 最终用于处理的本地文件（合并后/拉取后）
        String coverKey = null;     // 存到 fileCover 的 key

        try {
            // 1) 准备本地输入文件
            localInput = targetRoot.resolve(fileInfo.getFilePath());
            Files.createDirectories(localInput.getParent());
            if (isMinioEnabled()) {
                // 从 MinIO 拉取 objectKey 到 localInput
                pullFileFromMinioToLocal(fileInfo.getFilePath(), localInput);
            } else {
                // 合并分片到 localInput
                FileTools.union(tempFolder, localInput, true);
            }

            // 2) 根据类型生成衍生物
            FileTypeEnum ft = FileTypeEnum.getByType(fileInfo.getFileType());

            if (FileTypeEnum.VIDEO == ft) {
                VideoTools.cutVideo(fileInfo.getFileId(), localInput);

                coverKey = StringTools.removeSuffix(fileInfo.getFilePath()) + Constants.VIDEO_COVER_SUFFIX;
                Path coverPath = targetRoot.resolve(coverKey);
                VideoTools.createCover4Video(localInput, Constants.THUMBNAIL_WIDTH, coverPath);

                if (isMinioEnabled()) {
                    // 上传切片/封面
                    uploadVideoProductsToStorage(fileInfo.getFilePath(), fileInfo.getFileId(), targetRoot);
                }

            } else if (FileTypeEnum.IMAGE == ft) {
                coverKey = fileInfo.getFilePath().replace(".", "_.");
                Path coverPath = targetRoot.resolve(coverKey);
                boolean created = ThumbnailTools.createThumbnail(localInput, Constants.THUMBNAIL_WIDTH, coverPath, false);
                if (!created) FileUtils.copyFile(localInput.toFile(), coverPath.toFile());

                if (isMinioEnabled()) {
                    try (InputStream in = Files.newInputStream(coverPath)) {
                        oss().save(coverKey, in, Files.size(coverPath), null);
                    }
                }

            } else if (FileTypeEnum.AUDIO == ft) {
                String cover = StringTools.removeSuffix(fileInfo.getFilePath()) + Constants.AUDIO_COVER_SUFFIX;
                Path coverPath = targetRoot.resolve(cover);
                boolean coverCreated = AudioTools.extractCover(localInput, coverPath);
                if (!coverCreated) return;

                if (isMinioEnabled()) {
                    try (InputStream in = Files.newInputStream(coverPath)) {
                        oss().save(cover, in, Files.size(coverPath), Constants.AUDIO_COVER_TYPE);
                    }
                }

                String thumbKey = cover.replace(".", "_.");
                Path thumbPath = targetRoot.resolve(thumbKey);
                boolean thumbCreated = ThumbnailTools.createThumbnail(coverPath, Constants.THUMBNAIL_WIDTH, thumbPath, false);
                if (!thumbCreated) FileUtils.copyFile(coverPath.toFile(), thumbPath.toFile());

                if (isMinioEnabled()) {
                    try (InputStream in = Files.newInputStream(thumbPath)) {
                        oss().save(thumbKey, in, Files.size(thumbPath), "image/png");
                    }
                }
                coverKey = thumbKey;
            }

        } catch (Exception e) {
            ok = false;
            log.error("transcodeFile failed, fileId={}, userId={}", fileInfo.getFileId(), fileInfo.getUserId(), e);
        } finally {
            long size = 0;
            if (ok && localInput != null && Files.exists(localInput)) {
                try { size = Files.size(localInput); } catch (Exception ignore) {}
            }

            int newStatus = ok ? FileStatusEnum.ACTIVE.getStatus() : FileStatusEnum.TRANSCODE_FAILED.getStatus();

            // 这里用一个“带事务”的 finalize 方法更新 DB
            fileInfoService.finalizeTranscoding(fileInfo.getUserId(), fileInfo.getFileId(), size, coverKey, newStatus);

            // 清理 Local 临时目录 / Minio 本地拉取文件
            try {
                if (tempFolder != null && Files.exists(tempFolder)) {
                    FileUtils.deleteDirectory(tempFolder.toFile());
                }
            } catch (Exception e) {
                log.warn("delete tempFolder failed: {}", tempFolder, e);
            }

            if (isMinioEnabled()) {
                try {
                    fileCleanService.cleanupLocalFileArtifacts(fileInfo);
                } catch (Exception e) {
                    log.warn("cleanupLocalFileArtifacts failed, fileId={}", fileInfo.getFileId(), e);
                }
            }
        }
    }

    /**
     * 从 minio 拉取上传后的文件，以备生成图片缩略图，或者视频封面与切片
     */
    private void pullFileFromMinioToLocal(String objectKey, Path targetFile) {
        if (!isMinioEnabled()) return;
        Path tmp = targetFile.resolveSibling(targetFile.getFileName().toString() + ".tmp");
        try {
            // 1) 先写 tmp
            try (InputStream in = oss().get(objectKey)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            // 2) tmp -> target（优先原子 move，失败降级）
            try {
                Files.move(tmp, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.error("pullFileFromMinioToLocal failed, key={}, target={}", objectKey, targetFile, e);
            throw (e instanceof BusinessException be) ? be : new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {  // 不管成功失败，tmp 都清
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }

    private void uploadVideoProductsToStorage(String objectKey, String fileId, Path targetRoot) {
        if (!isMinioEnabled()) return;
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(fileId) || targetRoot == null)
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);

        ObjectStorageService os = oss();
        // 1) 上传封面：<noSuffix>.png
        String base = StringTools.removeSuffix(objectKey);
        String coverKey = base + Constants.VIDEO_COVER_SUFFIX;
        Path coverPath = targetRoot.resolve(coverKey);
        try (InputStream in = Files.newInputStream(coverPath)) {
            os.save(coverKey, in, Files.size(coverPath), Constants.VIDEO_COVER_TYPE);
        } catch (Exception e) {
            log.error("upload video cover failed: local={}, key={}", coverPath, coverKey, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        // 2) 上传切片目录
        Path segmentsDir = targetRoot.resolve(base); // 本地切片目录：.../file/202512/userId/fileId/
        Path m3u8Path = segmentsDir.resolve(Constants.M3U8_NAME);

        // 2.1 m3u8
        String m3u8Key = base + "/" + Constants.M3U8_NAME;
        try (InputStream in = Files.newInputStream(m3u8Path)) {
            os.save(m3u8Key, in, Files.size(m3u8Path), "application/vnd.apple.mpegurl");
        } catch (Exception e) {
            log.error("upload m3u8 failed: local={}, key={}", m3u8Path, m3u8Key, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        // 2.2 ts（fileId_0001.ts等）
        try (Stream<Path> s = Files.list(segmentsDir)) {
            for (Path p : s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".ts"))
                    .toList()) {
                String tsKey = base + "/" + p.getFileName();
                try (InputStream in = Files.newInputStream(p)) {
                    os.save(tsKey, in, Files.size(p), "video/mp2t");
                } catch (Exception e) {
                    log.error("upload ts failed: local={}, key={}", p, tsKey, e);
                    throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("list/upload ts failed: dir={}", segmentsDir, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }
}
