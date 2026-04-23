package com.mypan.service.avatar.impl;

import com.mypan.common.constants.Constants;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.dto.responseWrite.ObjMeta;
import com.mypan.service.file.storage.BasicStorageService;
import com.mypan.service.file.storage.ObjectStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "storage",
        name = "type",
        havingValue = "minio"
)
public class MinioAvatarService extends AbstractAvatarService {

    private final ObjectStorageService storageService;

    private static final String DEFAULT_KEY = Constants.FILE_FOLDER_AVATAR_NAME + "/" + Constants.DEFAULT_AVATAR_NAME;

    @Override
    public FileReadResourceDto openAvatarForRead(String userId) {
        String avatarKey = Constants.FILE_FOLDER_AVATAR_NAME + "/" + userId + Constants.AVATAR_SUFFIX;
        ObjMeta meta = storageService.statIfExists(avatarKey);
        if (meta == null) {
            meta = storageService.statIfExists(DEFAULT_KEY);
            avatarKey = DEFAULT_KEY;
        }
        FileReadResourceDto res = new FileReadResourceDto();
        res.setContentLength(meta.getSize());
        String ct = meta.getContentType();
        res.setContentType(StringUtils.hasText(ct) ? ct : Constants.AVATAR_TYPE);
        String finalKey = avatarKey;
        res.setOpenStream(() -> storageService.get(finalKey));
        return res;
    }

    @Override
    protected BasicStorageService storage() {
        return storageService;
    }

    @PostConstruct
    public void checkDefaultAvatarExists() {
        ObjMeta meta = storageService.statIfExists(DEFAULT_KEY);
        if (meta == null) {
            log.error("默认头像不存在，key={}", DEFAULT_KEY);
            throw new IllegalStateException(
                    "Default avatar is missing in MinIO: " + DEFAULT_KEY
            );
        }
        log.info("Default avatar found in MinIO: key={}, size={}, contentType={}",
                DEFAULT_KEY, meta.getSize(), meta.getContentType());
    }
}
