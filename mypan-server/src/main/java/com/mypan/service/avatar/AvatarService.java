package com.mypan.service.avatar;

import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {

    FileReadResourceDto openAvatarForRead(String userId);

    void saveAvatar(String userId, MultipartFile avatar);

}
