package com.mypan.infra.jpa.querydsl.file;

import com.mypan.infra.jpa.entity.QFileShare;
import com.mypan.infra.jpa.querydsl.support.OrderKeyParser;
import com.mypan.web.dto.query.FileShareQuery;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.mypan.infra.jpa.querydsl.support.QueryDslPredicates.*;

public class FileShareQueryDsl {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FileShareQueryDsl() {}

    /**
     * where 条件
     */
    public static BooleanBuilder buildPredicate(FileShareQuery query) {
        QFileShare q = QFileShare.fileShare;
        BooleanBuilder b = new BooleanBuilder();

        // ====== 权限（后端注入）======
        andEqText(b, q.userId, query.getUserId());

        // ====== 精确定位 ======
        andEqText(b, q.shareId, query.getShareId());
        andEqText(b, q.fileId, query.getFileId());

        // ====== 业务筛选 ======
        andEq(b, q.expireType, query.getExpireType());
        andEqText(b, q.code, query.getCode());
        andContains(b, q.code, query.getCodeFuzzy(), false);

        // ====== 时间范围（闭区间）======
        betweenDateTime(b, q.expiredAt, query.getExpiredAtStart(), query.getExpiredAtEnd(), DT_FMT);
        betweenDateTime(b, q.sharedAt, query.getSharedAtStart(), query.getSharedAtEnd(), DT_FMT);

        return b;
    }

    /**
     * orderKey -> OrderSpecifiers（白名单）
     * 支持单字段或多字段：
     * - "sharedAt_desc"
     * - "viewCount_desc,sharedAt_desc"
     */
    public static List<OrderSpecifier<?>> buildOrderSpecifiers(FileShareQuery query) {
        QFileShare q = QFileShare.fileShare;

        List<OrderSpecifier<?>> orders = OrderKeyParser.parse(
                query.getOrderKey(),
                3,
                (field, asc) -> mapOrderField(q, field, asc)
        );

        if (orders.isEmpty()) {
            // 分享列表默认：最近分享在前
            return List.of(q.sharedAt.desc());
        }
        return orders;
    }

    private static OrderSpecifier<?> mapOrderField(QFileShare q, String field, boolean asc) {
        return switch (field) {
            case "sharedAt" -> asc ? q.sharedAt.asc() : q.sharedAt.desc();
            case "expiredAt" -> asc ? q.expiredAt.asc() : q.expiredAt.desc();
            case "viewCount" -> asc ? q.viewCount.asc() : q.viewCount.desc();
            case "expireType" -> asc ? q.expireType.asc() : q.expireType.desc();
            default -> null;
        };
    }
}