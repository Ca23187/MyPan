package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.response.ResponseVo;
import com.mypan.infra.jpa.entity.FileShare;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.service.share.ShareInfoService;
import com.mypan.web.dto.query.FileShareQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.share.FileShareVo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/share")
@RequiresLogin
@RequiredArgsConstructor
public class ShareController {

    private final ShareInfoService shareInfoService;

    @GetMapping("/loadShareList")
    public ResponseVo<PaginationResultVo<FileShareVo>> loadShareList(FileShareQuery query) {
        return ResponseVo.ok(
                shareInfoService.pageMyShares(LoginUser.currentUserId(), query)
        );
    }

    @PostMapping("/shareFile")
    public ResponseVo<FileShare> shareFile(@NotBlank String fileId,
                                           @NotNull Integer expireType,
                                           String code) {
        return ResponseVo.ok(shareInfoService.createShare(LoginUser.currentUserId(), fileId, expireType, code));
    }

    @PostMapping("/cancelShare")
    public ResponseVo<Void> cancelShare(@NotBlank String shareIds) {
        shareInfoService.deleteShares(shareIds, LoginUser.currentUserId());
        return ResponseVo.ok(null);
    }
}
