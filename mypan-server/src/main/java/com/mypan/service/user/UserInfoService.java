package com.mypan.service.user;

import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.web.dto.query.UserInfoQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.user.UserInfoVo;
import com.mypan.web.dto.response.user.UserProfileVo;

public interface UserInfoService {
    void register(String email, String nickname, String password, String emailCode);

    LoginUser login(String email, String password);

    void resetPwd(String email, String password, String emailCode);

    void updateQqAvatarByUserId(String s, String userId);

    void updatePassword(String oldPassword, String newPassword, String userId);

    LoginUser qqLogin(String code);

    PaginationResultVo<UserInfoVo> pageUserList(String userId, UserInfoQuery query);

    void updateUserStatus(String operatorId, String targetId, Integer status);

    void clearUserFiles(String operatorId, String targetId);

    void addUserTotalSpace(String operatorId, String targetId, Integer newSpace);

    UserProfileVo getUserProfileVo(String userId);

    void addUsedSpace(String userId, long fileSize);
}
