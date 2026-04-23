package com.mypan.service.dto.share;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShareInfoDto {
    private LocalDateTime sharedAt;
    private LocalDateTime expiredAt;
    private String nickname;
    private String fileName;
    private String fileId;
    private String avatar;
    private String userId;
    private Integer delFlag;
    private Integer userStatus;
    private String shareCode;
    private String shareId;
}