package com.mypan.service.dto.download;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public final class DownloadRequestDto {
    private String userId;              // 建议绑定用户（避免码泄露）
    private List<String> selectedIds;   // 根节点 fileId（文件/文件夹都允许）
}
