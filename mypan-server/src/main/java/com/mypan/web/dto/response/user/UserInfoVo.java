package com.mypan.web.dto.response.user;

import com.mypan.infra.jpa.entity.QUserInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.querydsl.core.annotations.QueryProjection;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public final class UserInfoVo implements Serializable {

    @QueryProjection
    public UserInfoVo(String userId, String nickname, String email, String qqAvatar, LocalDateTime createdAt, LocalDateTime lastLoginAt, Integer status, Long usedSpace, Long totalSpace, Boolean admin) {
        this.userId = userId;
        this.nickname = nickname;
        this.email = email;
        this.qqAvatar = qqAvatar;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
        this.status = status;
        this.usedSpace = usedSpace;
        this.totalSpace = totalSpace;
        this.admin = admin;
    }

    public static Expression<UserInfoVo> selectBase(QUserInfo u, List<String> adminIds) {
        var isAdmin =
                new CaseBuilder()
                        .when(u.userId.in(adminIds)).then(true)
                        .otherwise(false);
        return new QUserInfoVo(
                u.userId,
                u.nickname,
                u.email,
                u.qqAvatar,
                u.createdAt,
                u.lastLoginAt,
                u.status,
                u.usedSpace,
                u.totalSpace,
                isAdmin
        );
    }

    private final String userId;

    private final String nickname;

    private final String email;

    private final String qqAvatar;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    private final Integer status;

    private final Long usedSpace;

    private final Long totalSpace;

    private final Boolean admin;
}
