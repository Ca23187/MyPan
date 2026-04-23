package com.mypan.infra.jpa.repository;

import com.mypan.infra.jpa.entity.FileShare;
import com.mypan.service.dto.share.ShareInfoDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;

public interface FileShareRepository extends JpaRepository<FileShare, String> {

    Integer deleteByUserIdAndShareIdIn(String userId, Collection<String> shareIds);

    @Query("""
        select new com.mypan.service.dto.share.ShareInfoDto(
            s.sharedAt,
            s.expiredAt,
            u.nickname,
            f.fileName,
            s.fileId,
            u.qqAvatar,
            u.userId,
            f.delFlag,
            u.status,
            s.code,
            s.shareId
        )
        from FileShare s
        join FileInfo f
             on f.fileId = s.fileId and f.userId = s.userId
        join UserInfo u
             on u.userId = s.userId
        where s.shareId = :shareId
        """)
    ShareInfoDto getShareInfoDto(String shareId);

    @Modifying
    @Query("""
        update FileShare s set s.viewCount = s.viewCount + 1 where s.shareId = :shareId
    """)
    void incrViewCount(String shareId);

    int deleteByExpiredAtBefore(LocalDateTime now);


    @Modifying
    @Query(value = """
    delete s
    from file_share s
    left join file_info f on f.file_id = s.file_id
    where s.expire_time is null
      and (
           f.file_id is null
        or f.del_flag = :delFlag
      )
    """, nativeQuery = true)
    int deleteForeverSharesIfFileMissingOrDeleted(Integer delFlag);
}