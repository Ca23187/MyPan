package com.mypan.common.utils.file;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.process.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;


@Slf4j
public final class ThumbnailTools {
    
    private ThumbnailTools() {}

    /**
     * 按宽度生成缩略图：
     * - 如果原图宽度 <= thumbnailWidth：不压缩，返回 false
     * - 否则调用 ffmpeg 压缩，返回 true
     */
    public static boolean createThumbnail(Path file,
                                          int thumbnailWidth,
                                          Path targetFile,
                                          boolean delSource) {
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) {
                log.error("读取图片失败，文件可能不是有效图片: {}", file);
                return false;
            }

            int sourceW = src.getWidth();
            // 小于指定宽度不压缩
            if (sourceW <= thumbnailWidth) {
                return false;
            }

            compressImage(file, thumbnailWidth, targetFile, delSource);
            return true;
        } catch (IOException e) {
            log.error("读取图片失败: {}", file, e);
        } catch (BusinessException e) {
            log.error("生成缩略图失败: {}", file, e);
        } catch (Exception e) {
            log.error("生成缩略图出现未知异常: {}", file, e);
        }
        return false;
    }

    /**
     * 按宽度百分比压缩图片（例如 0.7 = 宽度压缩到 70%）
     * 压缩完成后删除源文件
     */
    public static void compressImageWidthPercentage(Path sourceFile,
                                                    BigDecimal widthPercentage,
                                                    Path targetFile) {
        try {
            if (widthPercentage == null) {
                throw new IllegalArgumentException("widthPercentage 不能为空");
            }
            BufferedImage src = ImageIO.read(sourceFile.toFile());
            if (src == null) {
                log.warn("读取图片 {} 失败，文件可能不是有效图片", sourceFile);
                throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            }

            int sourceWidth = src.getWidth();
            int targetWidth = widthPercentage
                    .multiply(new BigDecimal(sourceWidth))
                    .intValue();

            if (targetWidth <= 0) {
                log.warn("计算后的目标宽度非法: {}", targetWidth);
                throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            }

            // 按新宽度压缩，并删除源文件
            compressImage(sourceFile, targetWidth, targetFile, true);
        } catch (BusinessException e) {
            log.error("按比例压缩图片失败, source={}", sourceFile, e);
            throw e;
        } catch (Exception e) {
            log.error("按比例压缩图片失败, source={}", sourceFile, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    /**
     * 按给定宽度压缩图片，保持宽高比，高度自适应
     *
     * @param delSource 是否在压缩成功后删除源文件
     */
    public static void compressImage(Path sourceFile,
                                     Integer width,
                                     Path targetFile,
                                     boolean delSource) {
        // 输出先写到 tmp
        Path tmp = FileTools.getTmpPath(targetFile);

        // ffmpeg -y -i input -vf scale=WIDTH:-1 output
        List<String> cmd = Arrays.asList(
                "ffmpeg",
                "-y",
                "-i", sourceFile.toAbsolutePath().toString(),
                "-vf", "scale=" + width + ":-1",
                tmp.toAbsolutePath().toString()
        );

        int exitCode = ProcessUtils.exec(cmd, sourceFile.getParent(), false);
        if (exitCode != 0) {
            // 失败清理 tmp
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            log.error("压缩图片失败, exitCode={}, source={}, target={}",
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
            log.error("移动压缩图片失败, source={}, target={}", sourceFile, targetFile, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }

        if (delSource) {
            try {
                Files.deleteIfExists(sourceFile);
            } catch (IOException e) {
                log.warn("压缩图片后删除源文件失败: {}", sourceFile, e);
            }
        }
    }
}
