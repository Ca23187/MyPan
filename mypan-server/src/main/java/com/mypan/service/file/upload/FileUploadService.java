package com.mypan.service.file.upload;

import com.mypan.web.dto.request.UploadInitRequestDto;
import com.mypan.web.dto.response.upload.UploadSessionVo;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    UploadSessionVo resumeUpload(String userId, String fileId);

    UploadSessionVo initUpload(String userId, UploadInitRequestDto req);

    UploadSessionVo uploadFile(String userId,
                               String fileId,
                               MultipartFile file,
                               String fileName,
                               String filePid,
                               String fileMd5,
                               Integer chunkIndex,
                               Integer chunks);

    UploadSessionVo abortUpload(String userId, String fileId);
}
