package com.mypan.service.file.storage.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.config.AppProperties;
import com.mypan.service.file.storage.BasicStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalStorageService implements BasicStorageService {

    private final AppProperties appProperties;

    private Path resolvePath(String objectKey) {
        return Paths.get(appProperties.getProjectFolder(), Constants.FILE_FOLDER_FILE, objectKey);
    }

    @Override
    public void save(String objectKey, InputStream in, long size, String contentType) {
        if (!StringUtils.hasText(objectKey) || in == null) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        Path target = resolvePath(objectKey);
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            // 写入 tmp（覆盖旧 tmp）
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        } finally {  // tmp 永远尽量清
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Path path = resolvePath(objectKey);
        if (!Files.exists(path) || Files.isDirectory(path)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_READABLE);
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path path = resolvePath(objectKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }
}
