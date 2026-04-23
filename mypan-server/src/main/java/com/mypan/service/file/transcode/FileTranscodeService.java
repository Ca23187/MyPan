package com.mypan.service.file.transcode;

import com.mypan.infra.jpa.entity.FileInfo;

import java.nio.file.Path;

public interface FileTranscodeService {
    void transcodeFile(FileInfo fileInfo, Path tempFolder, Path targetRoot);
}
