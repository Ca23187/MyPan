package com.mypan.service.file.download;

import com.mypan.service.dto.download.DownloadPlan;

public interface FileDownloadService {
    String createDownloadUrl(String fileIds, String userId);

    DownloadPlan resolveDownloadPlan(String code);
}
