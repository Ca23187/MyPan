package com.mypan.infra.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息
 */
@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
public class UserInfo implements Serializable {

    /**
     * 用户ID
     */
    @Id
    private String userId;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * qq openID
     */
    private String qqOpenId;

    /**
     * qq 头像
     */
    private String qqAvatar;


    /**
     * 密码
     */
    private String password;

    /**
     * 加入时间
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 0:禁用 1:正常
     */
    private Integer status;

    /**
     * 使用空间单位byte
     */
    private Long usedSpace;

    /**
     * 总空间单位byte
     */
    private Long totalSpace;

}
