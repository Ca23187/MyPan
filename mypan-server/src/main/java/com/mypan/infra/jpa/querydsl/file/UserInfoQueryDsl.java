package com.mypan.infra.jpa.querydsl.file;

import com.mypan.infra.jpa.entity.QUserInfo;
import com.mypan.infra.jpa.querydsl.support.OrderKeyParser;
import com.mypan.web.dto.query.UserInfoQuery;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.mypan.infra.jpa.querydsl.support.QueryDslPredicates.*;

public final class UserInfoQueryDsl {

    private UserInfoQueryDsl() {}

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 只负责 where */
    public static BooleanBuilder buildPredicate(UserInfoQuery query) {
        QUserInfo q = QUserInfo.userInfo;
        BooleanBuilder b = new BooleanBuilder();
        if (query == null) return b;

        // 精确定位（排障/管理端常用）
        andEqText(b, q.userId, query.getUserId());

        // 模糊搜索（管理端常用）
        andContains(b, q.nickname, query.getNicknameFuzzy(), true);
        andContains(b, q.email, query.getEmailFuzzy(), true);

        // 三方标识（精确）
        andEqText(b, q.qqOpenId, query.getQqOpenId());

        // 状态
        andEq(b, q.status, query.getStatus());

        // 时间范围
        betweenDateTime(b, q.createdAt, query.getCreatedAtStart(), query.getCreatedAtEnd(), DT_FMT);
        betweenDateTime(b, q.lastLoginAt, query.getLastLoginAtStart(), query.getLastLoginAtEnd(), DT_FMT);

        // 空间范围（可选）
        if (query.getUsedSpaceMin() != null) b.and(q.usedSpace.goe(query.getUsedSpaceMin()));
        if (query.getUsedSpaceMax() != null) b.and(q.usedSpace.loe(query.getUsedSpaceMax()));
        if (query.getTotalSpaceMin() != null) b.and(q.totalSpace.goe(query.getTotalSpaceMin()));
        if (query.getTotalSpaceMax() != null) b.and(q.totalSpace.loe(query.getTotalSpaceMax()));

        return b;
    }

    /**
     * 规则：
     * 1) 管理员置顶（admin -> 1, 非 admin -> 0，desc）
     * 2) 再按 orderKey / 默认排序
     *
     * 这样 service 层不再需要 orders.add(0, ...)。
     */
    public static List<OrderSpecifier<?>> buildOrderSpecifiers(
            UserInfoQuery query,
            List<String> adminIds
    ) {
        QUserInfo q = QUserInfo.userInfo;

        /*
         * 第一步：解析 orderKey / 默认排序
         */
        List<OrderSpecifier<?>> orders = new ArrayList<>(
                OrderKeyParser.parseOrDefault(
                        query == null ? null : query.getOrderKey(),
                        3,
                        (field, asc) -> mapOrderField(q, field, asc),
                        List.of(q.createdAt.desc())
                )
        );

        /*
         * 第二步：管理员置顶（优先级最高）
         * admin -> 1, 非 admin -> 0, desc
         */
        if (!CollectionUtils.isEmpty(adminIds)) {
            NumberExpression<Integer> isAdminRank =
                    new CaseBuilder()
                            .when(q.userId.in(adminIds)).then(1)
                            .otherwise(0);
            orders.add(0, isAdminRank.desc());
        }

        return orders;
    }

    /** 排序白名单：只允许这些字段被 orderKey 使用 */
    private static OrderSpecifier<?> mapOrderField(QUserInfo q, String field, boolean asc) {
        if (!StringUtils.hasText(field)) return null;

        return switch (field) {
            case "createdAt"     -> asc ? q.createdAt.asc()     : q.createdAt.desc();
            case "lastLoginAt" -> asc ? q.lastLoginAt.asc() : q.lastLoginAt.desc();
            case "usedSpace"     -> asc ? q.usedSpace.asc()     : q.usedSpace.desc();
            case "totalSpace"    -> asc ? q.totalSpace.asc()    : q.totalSpace.desc();
            case "nickname"      -> asc ? q.nickname.asc()      : q.nickname.desc();
            case "email"         -> asc ? q.email.asc()         : q.email.desc();
            default -> null;
        };
    }
}
