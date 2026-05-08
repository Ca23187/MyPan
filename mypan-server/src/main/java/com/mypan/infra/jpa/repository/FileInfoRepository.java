package com.mypan.infra.jpa.repository;

import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.entity.FileInfoId;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FileInfoRepository extends JpaRepository<FileInfo, FileInfoId> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update FileInfo f
        set f.fileSize = :fileSize,
            f.fileCover = :fileCover,
            f.status = :newStatus,
            f.lastModifiedAt = CURRENT_TIMESTAMP
        where f.fileId = :fileId
          and f.userId = :userId
          and f.status = :oldStatus
        """)
    int updateFileStatusWithOldStatus(String fileId, String userId, Integer oldStatus,
                                       Long fileSize, String fileCover, Integer newStatus);

    FileInfo findFirstByFileMd5AndStatus(String fileMd5, Integer status);

    FileInfo findFirstByUserId_AndFolderType_AndFileName_AndFilePid_AndDelFlag(String userId, Integer folderType, String fileName, String filePid, Integer DelFlag);

    @Query("""
    select new com.mypan.web.dto.response.file.FolderVo(f.fileName, f.fileId, f.lastModifiedAt)
    from FileInfo f where f.userId = :userId and f.folderType = :folderType and f.delFlag = :delFlag and f.fileId in :ids
    """)
    List<FolderVo> findFolderInfoVoList(String userId, Integer folderType, Integer delFlag, List<String> ids);

    @Query("""
    select new com.mypan.web.dto.response.file.FileInfoVo(
        f.fileId,
        f.userId,
        f.filePid,
        f.fileSize,
        f.fileName,
        f.fileCover,
        f.recycledAt,
        f.lastModifiedAt,
        f.folderType,
        f.fileCategory,
        f.fileType,
        f.status
    )
    from FileInfo f where f.fileId = :fileId and f.userId = :userId and f.delFlag = :delFlag
    """)
    FileInfoVo findVoByFileIdAndUserIdAndDelFlag(String fileId, String userId, Integer delFlag);

    @Modifying
    @Query("""
    update FileInfo f set f.fileName = :newFileName, f.lastModifiedAt = :now
    where f.fileId = :fileId and f.userId = :userId and f.fileName = :oldName and f.delFlag = :delFlag
    """)
    int renameWithOldName(String newFileName, LocalDateTime now, String fileId, String userId, String oldName, Integer delFlag);

    List<FileInfo> findByUserIdAndDelFlagAndFileIdIn(String userId, Integer delFlag, Collection<String> fileIds);

    FileInfo findByFileIdAndUserIdAndDelFlag(String fileId, String userId, Integer delFlag);

    @Query("""
    select f.fileName from FileInfo f
    where f.userId = :userId and f.filePid = :filePid and f.delFlag = :delFlag""")
    Set<String> findFileNameByUserIdAndFilePidAndDelFlag(String userId, String filePid, Integer delFlag);

    List<FileInfo> findByUserIdAndDelFlagAndFolderTypeAndFileIdIn(String userId, Integer flag, Integer type, Collection<String> ids);

    FileInfo findByFileIdAndUserId(String fileId, String userId);

    List<FileInfo> findByUserIdAndFilePidInAndDelFlag(String userId, List<String> filePids, Integer delFlag);

    @Query("update FileInfo f set f.delFlag = :delFlag, f.lastModifiedAt = CURRENT_TIMESTAMP where f.userId = :userId")
    @Modifying
    void updateDelFlagByUserId(Integer delFlag, String userId);

    List<FileInfo> findByUserIdAndDelFlagInAndFileIdIn(String userId, List<Integer> delFlags, List<String> rootIdList);

    List<FileInfo> findByUserIdAndFilePidInAndDelFlagIn(String userId, Collection<String> filePids, Collection<Integer> delFlags);

    List<FileInfo> findTop500ByDelFlagOrderByLastModifiedAtAsc(Integer flag);

    void deleteByFileIdIn(List<String> deleteIds);

    @Query("""
        select distinct f.filePath
        from FileInfo f
        where f.filePath in :paths
          and f.delFlag <> :delFlag
          and f.filePath is not null
          and f.filePath <> ''
    """)
    List<String> findReferencedPaths(Collection<String> paths, Integer delFlag);

    List<FileInfo> findTop500ByStatusInAndLastModifiedAtBefore(Collection<Integer> status, LocalDateTime stuckBefore);

    @Query("""
    select f.filePath
    from FileInfo f
    where f.fileId = :realFileId
      and f.delFlag = :usingFlag
      and f.filePath is not null
      and f.filePath <> ''
      and exists (
          select 1
          from FileInfo mine
          where mine.userId = :userId
            and mine.delFlag = :usingFlag
            and mine.filePath = f.filePath
      )
    """)
    String findReadableShareFilePathForTs(String realFileId, String userId, Integer usingFlag);

    List<FileInfo> findTop500ByDelFlagAndRecycledAtBeforeOrderByRecycledAtAsc(Integer flag, LocalDateTime before);

    // 全量重算 usedSpace：按 userId 分组汇总（只统计文件）
    @Query("""
       select f.userId as userId, sum(coalesce(f.fileSize,0)) as used
       from FileInfo f
       where f.folderType = :fileFolderType
         and f.delFlag in :delFlags
       group by f.userId
    """)
    List<UserUsedSpaceAgg> sumUsedSpaceByUser(Integer fileFolderType, Collection<Integer> delFlags);

    interface UserUsedSpaceAgg {
        String getUserId();
        Long getUsed();
    }

    @Query("""
    select c
    from FileInfo c
    left join FileInfo p
        on c.filePid = p.fileId
       and c.userId = p.userId
    where c.filePid is not null
      and c.filePid <> :rootPid
      and (
            p.fileId is null
         or p.folderType <> :folderType
      )
    """)
    List<FileInfo> findOrphanRecords(String rootPid, Integer folderType);

}
