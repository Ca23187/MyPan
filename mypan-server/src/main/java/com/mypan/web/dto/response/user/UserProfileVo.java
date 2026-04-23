package com.mypan.web.dto.response.user;

import lombok.Data;

@Data
public final class UserProfileVo {
    private String userId;
    private String nickname;
    private Boolean isAdmin;
}
