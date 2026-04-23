package com.mypan.service.file.download.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.FileFolderTypeEnum;
import com.mypan.common.enums.FileStatusEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.service.dto.download.DownloadPlan;
import com.mypan.service.dto.download.DownloadRequestDto;
import com.mypan.service.dto.download.ZipEntryResource;
import com.mypan.service.file.db.support.FileBatchOperationManager;
import com.mypan.service.file.download.FileDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FileDownloadServiceImpl implements FileDownloadService {

    private final FileInfoRepository repo;
    private final RedisComponent redisComponent;
    private final FileBatchOperationManager manager;

    @Override
    public String createDownloadUrl(String fileIds, String userId) {
        List<String> idList = StringTools.parseDelimitedDistinctList(fileIds, ",");
        if (idList.isEmpty()) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        // 校验：这些根节点必须属于该用户 & USING
        List<FileInfo> roots = repo.findByUserIdAndDelFlagAndFileIdIn(
                userId, FileDelFlagEnum.ACTIVE.getFlag(), idList
        );
        if (roots.isEmpty()) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);

        // 可选：强校验 “传入的 id 都存在且都属于用户”
        if (roots.size() != idList.size()) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        String code = StringTools.getRandomString(Constants.DOWNLOAD_CODE_LENGTH);

        DownloadRequestDto dto = new DownloadRequestDto();
        dto.setUserId(userId);
        dto.setSelectedIds(idList);
        redisComponent.saveDownloadRequestDto(code, dto);
        return code;
    }


    @Override
    public DownloadPlan resolveDownloadPlan(String code) {
        DownloadRequestDto dto = redisComponent.getDownloadRequestDto(code);
        if (dto == null) throw new BusinessException("Download code is invalid or expired.");

        String userId = dto.getUserId();
        List<String> rootIds = dto.getSelectedIds();

        // 查根节点（文件/文件夹）
        List<FileInfo> roots = repo.findByUserIdAndDelFlagAndFileIdIn(
                userId, FileDelFlagEnum.ACTIVE.getFlag(), rootIds
        );
        if (roots.isEmpty()) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        if (roots.size() != rootIds.size()) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        // 仅 1 个且是文件 → 单文件直下
        if (roots.size() == 1 && roots.get(0).getFolderType().equals(FileFolderTypeEnum.FILE.getType())) {
            FileInfo f = roots.get(0);

            if (!Objects.equals(f.getStatus(), FileStatusEnum.ACTIVE.getStatus()))
                throw new BusinessException("This file is not available for download.");

            DownloadPlan plan = new DownloadPlan();
            plan.setType(DownloadPlan.Type.SINGLE);
            plan.setObjectKey(f.getFilePath());
            plan.setFileName(f.getFileName());
            return plan;
        }

//        // 否则 → ZIP：递归展开所有文件
//        // 先查一下是不是同级
//        final List<ZipEntryResource> entries;
//        boolean samePid = roots.stream()
//                .map(r -> r.getFilePid() == null ? Constants.ROOT_PID : r.getFilePid())
//                .distinct()
//                .count() == 1;
//        if (samePid) {
//            entries = manager.buildZipEntriesSameLevelBfs(userId, roots);
//        } else {
//            // 跨级：增强逻辑放到 manager
//            entries = manager.buildZipEntriesMixedLevelBfs(userId, roots);
//        }
        List<ZipEntryResource> entries = manager.buildZipEntriesSameLevelBfs(userId, roots);
        DownloadPlan plan = new DownloadPlan();
        plan.setType(DownloadPlan.Type.ZIP);
        plan.setZipName("download.zip");
        plan.setEntries(entries);
        return plan;
    }
}
