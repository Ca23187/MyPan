package com.mypan.web.dto.response.share;

import com.mypan.infra.jpa.entity.QFileInfo;
import com.mypan.infra.jpa.entity.QFileShare;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.querydsl.core.annotations.QueryProjection;
import com.querydsl.core.types.Expression;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
public final class FileShareVo implements Serializable {

    @QueryProjection
    public FileShareVo(String shareId, String fileId, String userId, Integer expireType, LocalDateTime expiredAt, LocalDateTime sharedAt, String code, Integer viewCount, String fileName, Integer folderType, Integer fileCategory, Integer fileType, String fileCover) {
        this.shareId = shareId;
        this.fileId = fileId;
        this.userId = userId;
        this.expireType = expireType;
        this.expiredAt = expiredAt;
        this.sharedAt = sharedAt;
        this.code = code;
        this.viewCount = viewCount;
        this.fileName = fileName;
        this.folderType = folderType;
        this.fileCategory = fileCategory;
        this.fileType = fileType;
        this.fileCover = fileCover;
    }

    public static Expression<FileShareVo> selectWithFileInfo(QFileShare s, QFileInfo f) {
        return new QFileShareVo(
                s.shareId,
                s.fileId,
                s.userId,
                s.expireType,
                s.expiredAt,
                s.sharedAt,
                s.code,
                s.viewCount,
                f.fileName,
                f.folderType,
                f.fileCategory,
                f.fileType,
                f.fileCover
        );
    }

    private final String shareId;

    private final String fileId;

    private final String userId;

    private final Integer expireType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiredAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sharedAt;

    private final String code;

    private final Integer viewCount;

    private final String fileName;

    private final Integer folderType;

    private final Integer fileCategory;

    private final Integer fileType;

    private final String fileCover;

}
