package com.mypan.common.utils.file;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public final class FileTools {
    private FileTools() {}

    public static void union(Path dir, Path target, boolean delSource) {
        if (!Files.isDirectory(dir)) {
            log.warn("目录不存在: {}", dir);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        // 发现所有“数字命名”的分片
        List<Path> chunks;
        try {
            try (Stream<Path> s = Files.list(dir)) {
                chunks = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().matches("\\d+"))
                        .sorted(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString())))
                        .toList();
            }
        } catch (IOException e) {
            log.error("读取分片列表失败", e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        if (chunks.isEmpty()) {
            log.error("未找到任何分片");
            throw new BusinessException("未找到任何分片");
        }

        // 校验连续性：0..n-1 是否齐全
        for (int i = 0; i < chunks.size(); i++) {
            Path p = dir.resolve(String.valueOf(i));
            if (!Files.isRegularFile(p)) {
                log.error("分片缺失: {}", i);
                throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
            }
        }

        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");

        try {
            // 1) 合并到临时文件 tmp
            try (FileChannel out = FileChannel.open(tmp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                long pos = 0;
                for (Path c : chunks) {
                    try (FileChannel in = FileChannel.open(c, StandardOpenOption.READ)) {
                        long size = in.size();
                        long transferred = 0;
                        while (transferred < size) {
                            long n = out.transferFrom(in, pos + transferred, size - transferred);
                            if (n <= 0) {
                                throw new IOException("transferFrom returned " + n
                                        + ", chunk=" + c.getFileName()
                                        + ", expected=" + size
                                        + ", actual=" + transferred);
                            }
                            transferred += n;
                        }
                        // 强校验：任何不完整都当失败
                        if (transferred != size) {
                            throw new IOException("chunk copy incomplete, chunk=" + c.getFileName()
                                    + ", expected=" + size + ", actual=" + transferred);
                        }
                        pos += transferred;
                    }
                }
            }

            // 2) move tmp -> target（优先原子）
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("合并/落盘失败 target={}", target, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ex) {
                log.warn("清理 tmp 失败: {}", tmp, ex);
            }
        }

        // 3) 成功后删源分片目录
        if (delSource) {
            try {
                FileUtils.deleteDirectory(dir.toFile());
            } catch (IOException e) {
                log.warn("删除源分片目录失败: {}", dir, e);
            }
        }
    }

    /** 生成 tmp：把 .tmp 插到扩展名前，确保最后扩展名仍是 jpg/png/webp 等 */
    public static Path getTmpPath(Path targetFile) {
        String name = targetFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String base = name.substring(0, dot);
            String ext  = name.substring(dot); // includes "."
            return targetFile.resolveSibling(base + ".tmp" + ext); // e.g. cover.tmp.png
        }
        // 没扩展名就退化：xxx.tmp（这种情况下 ffmpeg 仍可能无法推断格式，建议业务上避免）
        return targetFile.resolveSibling(name + ".tmp");
    }
}
