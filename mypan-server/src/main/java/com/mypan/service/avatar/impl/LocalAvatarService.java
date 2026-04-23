package com.mypan.service.avatar.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.file.storage.BasicStorageService;
import com.mypan.service.file.storage.impl.LocalStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
@RequiredArgsConstructor
@Primary // 只是为了去掉 idea 报红，不影响运行
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalAvatarService extends AbstractAvatarService {

    private final LocalStorageService storageService;

    private final AppProperties appProperties;

    private static Path avatarRoot;

    @Override
    public FileReadResourceDto openAvatarForRead(String userId) {
        if (!StringTools.isPathSegmentOk(userId)) {
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }
        Path avatarPath = avatarRoot.resolve(userId + Constants.AVATAR_SUFFIX);
        if (!Files.isRegularFile(avatarPath) || !Files.isReadable(avatarPath)) {
            avatarPath = avatarRoot.resolve(Constants.DEFAULT_AVATAR_NAME);
        }
        try {
            String ct = Files.probeContentType(avatarPath);
            FileReadResourceDto res = new FileReadResourceDto();
            res.setContentLength(Files.size(avatarPath));
            res.setContentType(StringUtils.hasText(ct) ? ct : Constants.AVATAR_TYPE);

            Path finalPath = avatarPath;
            res.setOpenStream(() -> {
                try {
                    return Files.newInputStream(finalPath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return res;
        } catch (IOException e) {
            log.error("读取头像失败: {}", avatarPath, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }
    }

    @PostConstruct
    public void initAvatarRoot() {
        avatarRoot = Paths.get(
                appProperties.getProjectFolder(),
                Constants.FILE_FOLDER_FILE,
                Constants.FILE_FOLDER_AVATAR_NAME
        );

        try {
            Files.createDirectories(avatarRoot);
            log.info("Avatar root initialized: {}", avatarRoot);

            // 校验默认头像存在
            Path defaultAvatar = avatarRoot.resolve(Constants.DEFAULT_AVATAR_NAME);
            if (!Files.isRegularFile(defaultAvatar)) {
                log.error("默认头像缺失或不是文件: {}", defaultAvatar);
                throw new IllegalStateException("Default avatar missing: " + defaultAvatar);
            }
            if (!Files.isReadable(defaultAvatar)) {
                log.error("默认头像不可读: {}", defaultAvatar);
                throw new IllegalStateException("Default avatar not readable: " + defaultAvatar);
            }

            log.info("Default avatar ok: {}", defaultAvatar);

        } catch (IOException e) {
            log.error("初始化头像目录失败: {}", avatarRoot, e);
            throw new IllegalStateException("Failed to initialize avatar root: " + avatarRoot, e);
        }
    }

    @Override
    protected BasicStorageService storage() {
        return storageService;
    }
}
