package com.mypan.web.dto.response.file;

import com.mypan.infra.jpa.entity.QFileInfo;
import com.mypan.infra.jpa.entity.QUserInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.querydsl.core.annotations.QueryProjection;
import com.querydsl.core.types.Expression;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
public final class FileInfoVo implements Serializable {

    @QueryProjection
    public FileInfoVo(String fileId, String userId, String filePid, Long fileSize, String fileName, String fileCover, LocalDateTime recycledAt, LocalDateTime lastModifiedAt, Integer folderType, Integer fileCategory, Integer fileType, Integer status) {
        this.fileId = fileId;
        this.userId = userId;
        this.filePid = filePid;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.fileCover = fileCover;
        this.recycledAt = recycledAt;
        this.lastModifiedAt = lastModifiedAt;
        this.folderType = folderType;
        this.fileCategory = fileCategory;
        this.fileType = fileType;
        this.status = status;
    }

    // NOTE: 提供让 queryDSL 从实体类映射到 VO 类的 projection 的方法，注意必须与 @QueryProjection + 对应构造函数一起使用
    public static Expression<FileInfoVo> selectBase(QFileInfo f) {
        return new QFileInfoVo(
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
        );
    }

    /** 给多表查询字段 nickname 专门设置的构造函数 */
    @QueryProjection
    public FileInfoVo(String fileId,
                      String userId,
                      String filePid,
                      Long fileSize,
                      String fileName,
                      String fileCover,
                      LocalDateTime recycledAt,
                      LocalDateTime lastModifiedAt,
                      Integer folderType,
                      Integer fileCategory,
                      Integer fileType,
                      Integer status,
                      String nickname ) {
        this(fileId, userId, filePid, fileSize, fileName, fileCover,
                recycledAt, lastModifiedAt, folderType,
                fileCategory, fileType, status);
        this.nickname = nickname;
    }

    public static Expression<FileInfoVo> selectBaseWithUser(QFileInfo f, QUserInfo u) {
        return new QFileInfoVo(
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
                f.status,
                u.nickname
        );
    }

    private String fileId;

    private String userId;

    private String filePid;

    private Long fileSize;

    private String fileName;

    private String fileCover;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recycledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModifiedAt;

    private Integer folderType;

    private Integer fileCategory;

    private Integer fileType;

    private Integer status;

    private String nickname;
}
