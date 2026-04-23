package com.mypan.infra.security.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;


@Getter
@RequiredArgsConstructor
public final class LoginUser implements Serializable {
    private final String userId;

    public static LoginUser of(String userId) {
        return new LoginUser(userId);
    }

    /**
     * 获取当前请求的 LoginUser（可能为 null，表示未登录）
     */
    public static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser ? (LoginUser) principal : null;
    }

    /**
     * 获取当前用户 ID（未登录返回 null）
     */
    public static String currentUserId() {
        LoginUser u = current();
        return u == null ? null : u.getUserId();
    }

    /**
     * 是否已登录
     */
    public static boolean isLoggedIn() {
        return current() != null;
    }
}
