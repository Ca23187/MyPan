package com.mypan.common.utils.file;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.process.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

@Slf4j
public final class VideoTools {
    private VideoTools() {}

    public static Path cutVideo(String fileId, Path input) {
        if (input == null || !Files.isRegularFile(input)) {
            throw new IllegalArgumentException("路径不存在或不是文件: " + input);
        }

        Path parent = input.getParent();
        if (parent == null) parent = Paths.get(".").toAbsolutePath().normalize();

        String fileName = input.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = (dot > 0) ? fileName.substring(0, dot) : fileName;

        Path finalFolder = parent.resolve(baseName);
        Path tmpFolder   = parent.resolve(baseName + ".__tmp__");

        // 清理旧 tmp（避免脏数据）
        try {
            if (Files.exists(tmpFolder)) {
                FileUtils.deleteDirectory(tmpFolder.toFile());
            }
            Files.createDirectories(tmpFolder);
        } catch (IOException e) {
            throw new RuntimeException("无法创建输出目录: " + tmpFolder.toAbsolutePath(), e);
        }

        Path m3u8Path = tmpFolder.resolve(Constants.M3U8_NAME);
        Path segTmpl  = tmpFolder.resolve(fileId + "_%04d.ts");

        List<String> cmdCopy = Arrays.asList(
                "ffmpeg", "-y",
                "-i", input.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "copy",
                "-c:a", "copy",
                "-bsf:v", "h264_mp4toannexb",
                "-f", "hls",
                "-hls_time", "30",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", segTmpl.toAbsolutePath().toString(),
                m3u8Path.toAbsolutePath().toString()
        );

        List<String> cmd = Arrays.asList(
                "ffmpeg", "-y",
                "-i", input.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "h264",
                "-preset", "veryfast",
                "-profile:v", "main",
                "-level", "4.1",
                "-c:a", "aac",
                "-b:a", "128k",
                "-ac", "2",
                "-f", "hls",
                "-hls_time", "30",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", segTmpl.toAbsolutePath().toString(),
                m3u8Path.toAbsolutePath().toString()
        );

        try {
            int code = ProcessUtils.exec(cmdCopy, tmpFolder, false);
            if (code != 0) code = ProcessUtils.exec(cmd, tmpFolder, false);
            if (code != 0) throw new RuntimeException("ffmpeg 执行失败，退出码: " + code);

            // 目录替换：tmp -> final
            try {
                if (Files.exists(finalFolder)) {
                    FileUtils.deleteDirectory(finalFolder.toFile());
                }
            } catch (Exception ignored) {}

            try {
                Files.move(tmpFolder, finalFolder, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFolder, finalFolder, StandardCopyOption.REPLACE_EXISTING);
            }
            return finalFolder;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ffmpeg 执行异常", e);
        } finally {
            // 失败清理 tmp，避免堆积
            try {
                if (Files.exists(tmpFolder)) {
                    FileUtils.deleteDirectory(tmpFolder.toFile());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 为视频生成封面
     */
    public static void createCover4Video(Path sourceFile,
                                         Integer width,
                                         Path targetFile) {
        // 输出先写到 tmp
        Path tmp = FileTools.getTmpPath(targetFile);
        // 使用：ffmpeg -y -i input -vframes 1 -vf scale=WIDTH:-1 output
        // 让高度按比例缩放
        List<String> cmd = Arrays.asList(
                "ffmpeg",
                "-y",
                "-i", sourceFile.toAbsolutePath().toString(),
                "-frames:v", "1",
                "-vf", "scale=" + width + ":-1",
                tmp.toAbsolutePath().toString()
        );

        int exitCode = ProcessUtils.exec(cmd, sourceFile.getParent(), false);
        if (exitCode != 0) {  // 失败清理 tmp
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            log.error("生成视频封面失败, exitCode={}, source={}, target={}",
                    exitCode, sourceFile, targetFile);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        // 成功后 move tmp -> target（优先原子，失败降级）
        try {
            try {
                Files.move(tmp, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, targetFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.error("移动视频封面失败, source={}, target={}", sourceFile, targetFile, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
