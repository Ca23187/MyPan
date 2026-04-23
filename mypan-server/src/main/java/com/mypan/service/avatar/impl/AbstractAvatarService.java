package com.mypan.service.avatar.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.file.AvatarTools;
import com.mypan.service.avatar.AvatarService;
import com.mypan.service.file.storage.BasicStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

@Service
@Slf4j
public abstract class AbstractAvatarService implements AvatarService {

    protected abstract BasicStorageService storage();

    @Override
    public void saveAvatar(String userId, MultipartFile avatar) {
        if (avatar.isEmpty())
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        String objectKey = Constants.FILE_FOLDER_AVATAR_NAME + "/" + userId + Constants.AVATAR_SUFFIX;

        // 统一裁剪+缩放+输出PNG
        byte[] png = AvatarTools.normalizeAvatarToPng(
                avatar,
                256,                // outSize
                4096,               // maxSide
                16_000_000L,         // maxPixels
                2 * 1024 * 1024L     // maxUploadBytes
        );

        storage().save(
                objectKey,
                new ByteArrayInputStream(png),
                png.length,
                "image/png"
        );
    }
}
