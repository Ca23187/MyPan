package com.mypan.infra.jpa.querydsl.support;

import com.mypan.common.constants.Constants;
import com.mypan.web.dto.response.PaginationResultVo;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * QueryDSL 常用查询工具类
 *
 * 1) 保持原有单表 API 不变（findPageByParam / findListByParam / countByParam）
 * 2) 新增多表 Join 扩展能力：
 *    - findPageByCustomQuery：完全自定义 listQuery / countQuery（适合复杂 groupBy/having/子查询）
 *    - findPageByParamWithJoin：轻量 Join 分页（常用于 A leftJoin B 投影 VO）
 *
 * ⚠️ 注意：
 * - 多表 count 非常容易因为 join 放大，推荐用主键 countDistinct（例如 fileId）。
 * - count 查询不要被 orderBy/offset/limit/groupBy 影响，因此提供 joinOnlyConfigurer 与 listOnlyConfigurer 分离。
 */
@Component
public class QueryDslUtils {

    @Resource
    private JPAQueryFactory queryFactory;

    /**
     * predicate 防空包装：
     * - BooleanBuilder 为 null -> 返回 null
     * - BooleanBuilder 无条件 -> 返回 null（让 where 忽略它）
     * - 否则返回 Predicate
     */
    private Predicate nullSafe(BooleanBuilder b) {
        if (b == null) return null;
        return b.hasValue() ? b : null;
    }

    // =========================
    // ✅ 新增：Join/多表扩展能力
    // =========================

    /**
     * 多表分页（完全自定义版）：
     * 调用方自行构造 listQuery / countQuery，工具类只负责：
     * - 计算 offset/limit
     * - 执行 count & list
     * - 处理 orderBy
     *
     * 适用场景：
     * - 复杂 join（多张表）
     * - groupBy/having
     * - 子查询 / union-like 变体（通过子查询实现）
     *
     * @param listQueryBuilder  (factory, wherePredicate) -> JPAQuery<R>
     *                         要求：内部必须包含 select + from + (join...) + where(wherePredicate)
     * @param countQueryBuilder (factory, wherePredicate) -> JPAQuery<Long>
     *                         要求：内部必须包含 select(count / countDistinct) + from + (join...) + where(wherePredicate)
     */
    public <R> PaginationResultVo<R> findPageByCustomQuery(
            Integer pageNo,
            Integer pageSize,
            List<OrderSpecifier<?>> orders,
            BooleanBuilder predicate,
            BiFunction<JPAQueryFactory, Predicate, JPAQuery<R>> listQueryBuilder,
            BiFunction<JPAQueryFactory, Predicate, JPAQuery<Long>> countQueryBuilder
    ) {
        // 参数兜底
        int pNo = pageNo == null ? 1 : Math.max(1, pageNo);
        int pSize = pageSize == null ? Constants.PAGE_SIZE : Math.max(1, pageSize);
        int offset = (pNo - 1) * pSize;

        // wherePredicate（可能为 null）
        Predicate where = nullSafe(predicate);

        // ---- count ----
        Objects.requireNonNull(countQueryBuilder, "countQueryBuilder must not be null");
        JPAQuery<Long> countQuery = countQueryBuilder.apply(queryFactory, where);
        if (countQuery == null) {
            throw new IllegalArgumentException("countQueryBuilder returned null");
        }
        Long total = countQuery.fetchOne();
        long totalCount = total == null ? 0L : total;

        int totalPages = getPageTotal(totalCount, pSize);

        // ---- list ----
        Objects.requireNonNull(listQueryBuilder, "listQueryBuilder must not be null");
        JPAQuery<R> listQuery = listQueryBuilder.apply(queryFactory, where);
        if (listQuery == null) {
            throw new IllegalArgumentException("listQueryBuilder returned null");
        }

        // orderBy 统一由工具类处理（避免调用方重复写）
        if (orders != null && !orders.isEmpty()) {
            listQuery.orderBy(orders.toArray(new OrderSpecifier<?>[0]));
        }

        List<R> list = listQuery.offset(offset).limit(pSize).fetch();

        return new PaginationResultVo<>(totalCount, pSize, pNo, totalPages, list);
    }

    /**
     * Query 定制器（推荐用在 join/on 上）：
     * - joinOnlyConfigurer：只对 count/list 两个 query 共同生效的部分（一般只写 join/on）
     * - listOnlyConfigurer：只对 list query 生效（例如 fetchJoin / groupBy / having / 额外 where）
     *
     * ⚠️ 强烈建议：不要在 joinOnlyConfigurer / listOnlyConfigurer 里写 orderBy/offset/limit！
     * orderBy/offset/limit 由工具类统一控制，避免污染 count 查询或分页逻辑。
     */
    @FunctionalInterface
    public interface QueryConfigurer {
        void apply(JPAQuery<?> query);
    }

    /**
     * 多表 join 分页（轻量便捷版）：
     * - from 仍然只传一个主表（EntityPathBase）
     * - 通过 joinOnlyConfigurer 给 count/list 同步追加 join/on（例如 leftJoin user）
     * - 可选 listOnlyConfigurer 仅在 listQuery 生效（例如 fetchJoin、groupBy/having 等）
     * - count 使用 countDistinctKey.countDistinct()，避免 join 放大总数
     *
     * @param countDistinctKey 用于 countDistinct 的字段（建议传主键，比如 fileId）
     */
    public <T, R> PaginationResultVo<R> findPageByParamWithJoin(
            EntityPathBase<T> from,
            BooleanBuilder predicate,
            Integer pageNo,
            Integer pageSize,
            List<OrderSpecifier<?>> orders,
            Expression<R> projection,
            SimpleExpression<?> countDistinctKey,
            QueryConfigurer joinOnlyConfigurer,
            QueryConfigurer listOnlyConfigurer
    ) {
        // 参数兜底
        int pNo = pageNo == null ? 1 : Math.max(1, pageNo);
        int pSize = pageSize == null ? Constants.PAGE_SIZE : Math.max(1, pageSize);
        int offset = (pNo - 1) * pSize;

        Predicate where = nullSafe(predicate);

        // -------------------
        // count（禁止 orderBy）
        // -------------------
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(countDistinctKey, "countDistinctKey must not be null");

        JPAQuery<Long> countQuery = queryFactory
                .select(countDistinctKey.countDistinct())
                .from(from)
                .where(where);

        // join/on：仅允许写 join/on（不要写 orderBy/offset/limit/groupBy）
        if (joinOnlyConfigurer != null) {
            joinOnlyConfigurer.apply(countQuery);
        }

        Long total = countQuery.fetchOne();
        long totalCount = total == null ? 0L : total;
        int totalPages = getPageTotal(totalCount, pSize);

        // -------------------
        // list（允许 orderBy）
        // -------------------
        Objects.requireNonNull(projection, "projection must not be null");

        JPAQuery<R> listQuery = queryFactory
                .select(projection)
                .from(from)
                .where(where);

        // join/on：同 count
        if (joinOnlyConfigurer != null) {
            joinOnlyConfigurer.apply(listQuery);
        }
        // listOnly：只作用在 list（例如 fetchJoin / groupBy / having / 额外过滤）
        if (listOnlyConfigurer != null) {
            listOnlyConfigurer.apply(listQuery);
        }

        // orderBy 统一由工具类处理
        if (orders != null && !orders.isEmpty()) {
            listQuery.orderBy(orders.toArray(new OrderSpecifier<?>[0]));
        }

        List<R> list = listQuery.offset(offset).limit(pSize).fetch();

        return new PaginationResultVo<>(totalCount, pSize, pNo, totalPages, list);
    }

    /**
     * findPageByParamWithJoin 的简化重载：
     * 只有 join/on，无额外 listOnly 定制逻辑时使用。
     */
    public <T, R> PaginationResultVo<R> findPageByParamWithJoin(
            EntityPathBase<T> from,
            BooleanBuilder predicate,
            Integer pageNo,
            Integer pageSize,
            List<OrderSpecifier<?>> orders,
            Expression<R> projection,
            SimpleExpression<?> countDistinctKey,
            QueryConfigurer joinOnlyConfigurer
    ) {
        return findPageByParamWithJoin(
                from, predicate, pageNo, pageSize, orders, projection, countDistinctKey,
                joinOnlyConfigurer, null
        );
    }

    // -------------------------
    // 你原来的单表方法保持不变
    // -------------------------

    /**
     * 统计符合条件的总数（单表）
     * 注意：多表 join 不要用这个；请用 findPageByParamWithJoin 或 findPageByCustomQuery
     */
    public <T> long countByParam(EntityPathBase<T> from, BooleanBuilder predicate) {
        Long count = queryFactory.select(from.count())
                .from(from)
                .where(nullSafe(predicate))
                .fetchOne();
        return count != null ? count : 0L;
    }

    /**
     * 查询列表（单表），支持投影（VO/DTO）
     */
    public <T, R> List<R> findListByParam(EntityPathBase<T> from,
                                          BooleanBuilder predicate,
                                          Integer offset,
                                          Integer limit,
                                          List<OrderSpecifier<?>> orders,
                                          Expression<R> projection) {
        JPAQuery<R> query = queryFactory.select(projection)
                .from(from)
                .where(nullSafe(predicate));

        if (orders != null && !orders.isEmpty()) {
            query.orderBy(orders.toArray(new OrderSpecifier<?>[0]));
        }

        // 不分页：直接返回全部
        if (offset == null && limit == null) {
            return query.fetch();
        }

        // 只有 limit
        if (offset == null) {
            int safeLimit = Math.max(limit, 1);
            return query.limit(safeLimit).fetch();
        }

        // 只有 offset
        int safeOffset = Math.max(offset, 0);
        if (limit == null) {
            return query.offset(safeOffset).limit(Constants.PAGE_SIZE).fetch();
        }

        int safeLimit = Math.max(limit, 1);
        return query.offset(safeOffset).limit(safeLimit).fetch();
    }

    /**
     * 查询列表（单表），返回本体
     */
    public <T> List<T> findListByParam(EntityPathBase<T> from,
                                       BooleanBuilder predicate,
                                       Integer offset,
                                       Integer limit,
                                       List<OrderSpecifier<?>> orders) {
        return findListByParam(from, predicate, offset, limit, orders, from);
    }

    /**
     * 单表分页查询（投影）
     */
    public <T, R> PaginationResultVo<R> findPageByParam(EntityPathBase<T> from,
                                                        BooleanBuilder predicate,
                                                        Integer pageNo,
                                                        Integer pageSize,
                                                        List<OrderSpecifier<?>> orders,
                                                        Expression<R> projection) {
        int pNo = pageNo == null ? 1 : Math.max(1, pageNo);
        int pSize = pageSize == null ? Constants.PAGE_SIZE : Math.max(1, pageSize);
        int offset = (pNo - 1) * pSize;

        long totalCount = countByParam(from, predicate);
        int totalPages = getPageTotal(totalCount, pSize);
        List<R> list = findListByParam(from, predicate, offset, pSize, orders, projection);
        return new PaginationResultVo<>(totalCount, pSize, pNo, totalPages, list);
    }

    /**
     * 单表分页查询（本体）
     */
    public <T> PaginationResultVo<T> findPageByParam(EntityPathBase<T> from,
                                                     BooleanBuilder predicate,
                                                     Integer pageNo,
                                                     Integer pageSize,
                                                     List<OrderSpecifier<?>> orders) {
        return findPageByParam(from, predicate, pageNo, pageSize, orders, from);
    }

    /**
     * 计算总页数
     */
    public int getPageTotal(long totalCount, int pageSize) {
        if (totalCount == 0) return 0;
        return (int) ((totalCount + pageSize - 1) / pageSize);
    }
}
