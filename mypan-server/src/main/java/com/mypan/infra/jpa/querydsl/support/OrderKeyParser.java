package com.mypan.infra.jpa.querydsl.support;

import com.querydsl.core.types.OrderSpecifier;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * 将 orderKey（如 "fileName_asc,createdAt_desc"）解析为 OrderSpecifiers
 */
public final class OrderKeyParser {

    private OrderKeyParser() {}

    public static List<OrderSpecifier<?>> parse(
            String rawOrderKey,
            int maxFields,
            BiFunction<String, Boolean, OrderSpecifier<?>> mapper
    ) {
        if (!StringUtils.hasText(rawOrderKey)) return new ArrayList<>();

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        String[] tokens = rawOrderKey.split(",");

        for (String token : tokens) {
            if (!StringUtils.hasText(token)) continue;

            String[] parts = token.trim().split("_");
            if (parts.length != 2) continue;

            String field = parts[0];
            String dir = parts[1].toLowerCase(Locale.ROOT);
            boolean asc = "asc".equals(dir);
            boolean desc = "desc".equals(dir);
            if (!asc && !desc) continue;

            OrderSpecifier<?> os = mapper.apply(field, asc);
            if (os != null) orders.add(os);

            if (orders.size() >= maxFields) break;
        }
        return orders;
    }

    public static List<OrderSpecifier<?>> parseOrDefault(
            String rawOrderKey,
            int maxFields,
            BiFunction<String, Boolean, OrderSpecifier<?>> mapper,
            List<OrderSpecifier<?>> defaultOrders
    ) {
        List<OrderSpecifier<?>> orders = parse(rawOrderKey, maxFields, mapper);
        if (orders.isEmpty()) {
            return new ArrayList<>(defaultOrders == null ? List.of() : defaultOrders);
        }
        return orders;
    }

}
