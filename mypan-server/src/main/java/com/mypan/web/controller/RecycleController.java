package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.response.ResponseVo;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recycle")
@RequiresLogin
@RequiredArgsConstructor
public class RecycleController {

    private final FileInfoService fileInfoService;

    @GetMapping("/loadRecycleList")
    public ResponseVo<PaginationResultVo<FileInfoVo>> loadRecycleList(Integer pageNo, Integer pageSize) {
        return ResponseVo.ok(
                fileInfoService.pageMyRecycledFiles(LoginUser.currentUserId(), pageNo, pageSize)
        );
    }

    @PostMapping("/recoverFile")
    public ResponseVo<Void> recoverFile(@NotBlank String fileIds) {
        fileInfoService.recoverFiles(LoginUser.currentUserId(), fileIds);
        return ResponseVo.ok(null);
    }

    @PostMapping("/delFile")
    public ResponseVo<Void> delFile(@NotBlank String fileIds) {
        fileInfoService.delFilesUser(LoginUser.currentUserId(), fileIds);
        return ResponseVo.ok(null);
    }
}
