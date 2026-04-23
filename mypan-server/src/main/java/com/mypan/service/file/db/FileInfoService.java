package com.mypan.service.file.db;

import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.web.dto.query.FileInfoQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;

import java.util.List;

public interface FileInfoService {
    PaginationResultVo<FileInfoVo> pageMyFiles(String userId, FileInfoQuery query);

    FileInfo findByFileIdAndUserIdAndDelFlag(String fileId, String userId, Integer delFlag);

    FileInfo createFolder(String filePid, String userId, String folderName);

    List<FolderVo> getFolderInfoVoList(String path, String userId);

    FileInfoVo rename(String fileId, String userId, String fileName);

    List<FileInfoVo> findMovableTargetFolders(String userId, String filePid, String currentFileIds);

    void changeFileLocation(String fileIds, String filePid, String userId);

    void moveFiles2RecycleBin(String userId, String fileIds);

    PaginationResultVo<FileInfoVo> pageMyRecycledFiles(String userId, Integer pageNo, Integer pageSize);

    void recoverFiles(String userId, String fileIds);

    void delFilesUser(String userId, String fileIds);

    List<FolderVo> getFolderBreadcrumb(String userId, String fileId);

    PaginationResultVo<FileInfoVo> pageUserFiles(FileInfoQuery query);

    void delFilesAdmin(String fileIdAndUserIds);

    void checkInShareRoot(String fileId, String shareUserId, String filePid);

    PaginationResultVo<FileInfoVo> pageShareFiles(Integer pageNo, Integer pageSize, String filePid, ShareAccessDto access);

    void saveShareFiles(String fileId, String shareFileIds, String myFolderId, String shareUserId, String myUserId);

    String resolveBaseFolderForTsByDb(String realFileId, String userId);

    FileInfo findByFileIdAndUserId(String fileId, String userId);

    void finalizeTranscoding(String userId, String fileId, long size, String coverKey, int newStatus);
}
