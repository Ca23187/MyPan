package com.mypan.service.schedule.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileTypeEnum;
import com.mypan.common.utils.file.FileTools;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.service.schedule.FileCleanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileCleanServiceImpl implements FileCleanService {

    private final AppProperties appProperties;

    private final RedisComponent redisComponent;


    @Override
    public void cleanupLocalFileArtifacts(FileInfo fileInfo) {
        if (fileInfo == null) return;

        String filePath = fileInfo.getFilePath();
        if (!StringUtils.hasText(filePath)) return;

        Path fileRoot = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE);

        // 1) 合并后的本地成品文件：.../file/<filePath> 及可能存在的 tmp
        Path merged = fileRoot.resolve(filePath);
        try { Files.deleteIfExists(merged); } catch (Exception ignored) {}
        Path tmp = merged.resolveSibling(merged.getFileName().toString() + ".tmp");
        try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}

        FileTypeEnum type = FileTypeEnum.getByType(fileInfo.getFileType());
        if (type == FileTypeEnum.IMAGE) {
            // 2) 图片缩略图本地路径：.../file/<coverKey>
            Path thumbPath = fileRoot.resolve(filePath.replace(".", "_."));
            Path thumbTmpPath = FileTools.getTmpPath(thumbPath);  // 注意，由于ffmpeg直接输出到.tmp或报错，所以这里换了一套tmp写法
            try { Files.deleteIfExists(thumbPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(thumbTmpPath); } catch (Exception ignored) {}
            return;
        }

        if (type == FileTypeEnum.VIDEO) {
            // 2) 视频封面本地路径：.../file/<noSuffix>.png
            String base = StringTools.removeSuffix(filePath);
            String coverKey = base + Constants.VIDEO_COVER_SUFFIX;
            Path coverPath = fileRoot.resolve(coverKey);
            Path coverTmpPath = FileTools.getTmpPath(coverPath);
            try { Files.deleteIfExists(coverPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(coverTmpPath); } catch (Exception ignored) {}

            // 3) 视频切片目录：.../file/<noSuffix>/
            if (StringUtils.hasText(base)) {
                Path segmentsDir = fileRoot.resolve(base);
                Path tmpSegmentsDir = fileRoot.resolve(base + ".__tmp__");
                // 3.1 目录整体删掉（最彻底）
                try {
                    if (Files.exists(tmpSegmentsDir)) {
                        FileUtils.deleteDirectory(tmpSegmentsDir.toFile());
                    }
                } catch (Exception ignored) {}
                try {
                    if (Files.exists(segmentsDir)) {
                        FileUtils.deleteDirectory(segmentsDir.toFile());
                    }
                } catch (Exception e) {
                    log.warn("cleanupLocalFileArtifacts delete segmentsDir failed: {}", segmentsDir, e);

                    // 3.2 如果目录删不掉（可能被占用），至少把里面残留的文件清掉兜底
                    try (Stream<Path> s = Files.walk(segmentsDir)) {
                        s.filter(Files::isRegularFile)
                                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                    } catch (Exception ignored) {}
                }
            }
        }
        if (type == FileTypeEnum.AUDIO) {
            String coverKey = StringTools.removeSuffix(filePath) + Constants.AUDIO_COVER_SUFFIX;
            Path coverPath = fileRoot.resolve(coverKey);
            Path coverTmpPath = FileTools.getTmpPath(coverPath);
            Path thumbPath = fileRoot.resolve(coverKey.replace(".", "_."));
            Path thumbTmpPath = FileTools.getTmpPath(thumbPath);
            try { Files.deleteIfExists(coverPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(coverTmpPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(thumbPath); } catch (Exception ignored) {}
            try { Files.deleteIfExists(thumbTmpPath); } catch (Exception ignored) {}
        }
    }

    @Override
    public void cleanupUploadTemp(String userId, String fileId) {
        // temp folder: ${projectFolder}/temp/<userId+fileId>/
        Path tempFolder = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_TEMP, userId + fileId);
        redisComponent.clearTempUploadSize(userId, fileId);
        try {
            if (Files.exists(tempFolder)) {
                FileUtils.deleteDirectory(tempFolder.toFile());
            }
        } catch (Exception e) {
            log.warn("cleanupUploadTemp failed: {}", tempFolder, e);
        }
    }

    @Override
    public void purgeOrphanTempFolders(int olderThanHours) {
        Path tempRoot = Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_TEMP);
        if (!Files.exists(tempRoot)) return;

        LocalDateTime before = LocalDateTime.now().minusHours(Math.max(1, olderThanHours));

        try (Stream<Path> stream = Files.list(tempRoot)) {
            stream.forEach(p -> {
                try {
                    if (!Files.isDirectory(p)) return;
                    LocalDateTime lastMod = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(p).toInstant(),
                            ZoneId.systemDefault()
                    );
                    if (lastMod.isBefore(before)) {
                        FileUtils.deleteDirectory(p.toFile());
                    }
                } catch (Exception e) {
                    log.warn("purgeOrphanTempFolders delete failed: {}", p, e);
                }
            });
        } catch (Exception e) {
            log.warn("purgeOrphanTempFolders failed", e);
        }
    }

    @Override
    public void purgeLocalTmpFiles(int olderThanHours) {
        Path root = Paths.get(appProperties.getProjectFolder());
        if (!Files.exists(root)) return;

        LocalDateTime before = LocalDateTime.now().minusHours(Math.max(1, olderThanHours));
        try (Stream<Path> s = Files.walk(root)) {
            s.forEach(p -> {
                try {
                    // 取最后修改时间（文件/目录都可以取）
                    LocalDateTime lm = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());

                    if (!lm.isBefore(before)) return;

                    String name = p.getFileName().toString();

                    // 1) 处理 __tmp__ 目录
                    if (Files.isDirectory(p) && name.endsWith(".__tmp__")) {
                        try {
                            FileUtils.deleteDirectory(p.toFile());
                        } catch (Exception e) {
                            log.warn("purgeLocalTmpFiles delete tmp dir failed: {}", p, e);
                        }
                        return;
                    }

                    // 2) 处理 tmp 文件：旧规则 *.tmp + 新规则 *.tmp.*
                    if (Files.isRegularFile(p)) {
                        boolean isTmpFile = name.endsWith(".tmp") || name.contains(".tmp.");
                        if (isTmpFile) {
                            Files.deleteIfExists(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("purgeLocalTmpFiles delete failed: {}", p, e);
                }
            });
        } catch (Exception e) {
            log.warn("purgeLocalTmpFiles scan failed, root={}", root, e);
        }
    }

}
