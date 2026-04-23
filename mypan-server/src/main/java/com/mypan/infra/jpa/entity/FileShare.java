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

@Setter
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
public class FileShare implements Serializable {
    /**
     * 分享ID
     */
    @Id
    private String shareId;

    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 有效期类型 0:1天 1:7天 2:30天 3:永久有效
     */
    private Integer expireType;

    /**
     * 失效时间
     */
    private LocalDateTime expiredAt;

    /**
     * 分享时间
     */
    @CreatedDate
    private LocalDateTime sharedAt;

    /**
     * 提取码
     */
    private String code;

    /**
     * 浏览次数
     */
    private Integer viewCount;
}
