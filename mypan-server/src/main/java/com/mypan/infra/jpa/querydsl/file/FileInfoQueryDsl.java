package com.mypan.infra.jpa.querydsl.file;

import com.mypan.common.enums.SearchScopeEnum;
import com.mypan.infra.jpa.entity.QFileInfo;
import com.mypan.infra.jpa.querydsl.support.OrderKeyParser;
import com.mypan.web.dto.query.FileInfoQuery;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.mypan.infra.jpa.querydsl.support.QueryDslPredicates.*;

public class FileInfoQueryDsl {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FileInfoQueryDsl() {}

    public static BooleanBuilder buildPredicate(FileInfoQuery query) {
        QFileInfo q = QFileInfo.fileInfo;
        BooleanBuilder b = new BooleanBuilder();

        // ====== 权限/范围（通常由后端注入/固定）======
        andEqText(b, q.userId, query.getUserId());
        andEq(b, q.delFlag, query.getDelFlag());

        // ====== 搜索范围控制 ======
        SearchScopeEnum scope = SearchScopeEnum.ofOrDefault(query.getSearchScope(), SearchScopeEnum.SCOPE_CURRENT_ONLY);
        if (scope == SearchScopeEnum.SCOPE_CURRENT_ONLY) {
            andEqText(b, q.filePid, query.getFilePid());
        } else if (scope == SearchScopeEnum.SCOPE_CURRENT_RECURSIVE) {
            inIfNotEmpty(b, q.filePid, query.getFilePidIn());
        }

        // ====== 主键定位（通常仅管理端/排障）======
        andEqText(b, q.fileId, query.getFileId());
        inIfNotEmpty(b, q.fileId, query.getFileIdIn());
        notInIfNotEmpty(b, q.fileId, query.getExcludeFileIdIn());
        notInIfNotEmpty(b, q.userId, query.getExcludeUserIdIn());

        // ====== 文件名搜索（用户端核心）======
        // 文件名通常不区分大小写：containsIgnoreCase
        andContains(b, q.fileName, query.getFileNameFuzzy(), true);

        // ====== 类型筛选 ======
        andEq(b, q.folderType, query.getFolderType());
        andEq(b, q.fileCategory, query.getFileCategory());
        andEq(b, q.fileType, query.getFileType());
        andEq(b, q.status, query.getStatus());
        andNotEq(b, q.delFlag, query.getExcludeDelFlag());

        // ====== 时间范围（闭区间）======
        betweenDateTime(b, q.createdAt, query.getCreatedAtStart(), query.getCreatedAtEnd(), DT_FMT);
        betweenDateTime(b, q.lastModifiedAt, query.getLastModifiedAtStart(), query.getLastModifiedAtEnd(), DT_FMT);
        betweenDateTime(b, q.recycledAt, query.getRecycledAtStart(), query.getRecycledAtEnd(), DT_FMT);

        return b;
    }

    public static List<OrderSpecifier<?>> buildOrderSpecifiers(FileInfoQuery query) {
        QFileInfo q = QFileInfo.fileInfo;
        return OrderKeyParser.parseOrDefault(
                query.getOrderKey(), 3, (f,a) -> mapOrderField(q,f,a),
                List.of(q.folderType.desc(), q.lastModifiedAt.desc())
        );
    }

    private static OrderSpecifier<?> mapOrderField(QFileInfo q, String field, boolean asc) {
        return switch (field) {
            case "createdAt" -> asc ? q.createdAt.asc() : q.createdAt.desc();
            case "lastModifiedAt" -> asc ? q.lastModifiedAt.asc() : q.lastModifiedAt.desc();
            case "recycledAt" -> asc ? q.recycledAt.asc() : q.recycledAt.desc();
            case "fileName" -> asc ? q.fileName.asc() : q.fileName.desc();
            case "fileSize" -> asc ? q.fileSize.asc() : q.fileSize.desc();
            case "folderType" -> asc ? q.folderType.asc() : q.folderType.desc();
            case "fileType" -> asc ? q.fileType.asc() : q.fileType.desc();
            case "fileCategory" -> asc ? q.fileCategory.asc() : q.fileCategory.desc();
            default -> null;
        };
    }
}