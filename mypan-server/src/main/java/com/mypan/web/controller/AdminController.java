package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.annotation.StreamResponse;
import com.mypan.common.response.ResponseVo;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.service.dto.SysSettingsDto;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.download.FileDownloadService;
import com.mypan.service.user.UserInfoService;
import com.mypan.web.dto.query.FileInfoQuery;
import com.mypan.web.dto.query.UserInfoQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import com.mypan.web.dto.response.user.UserInfoVo;
import com.mypan.web.support.FileDeliveryFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiresLogin
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final RedisComponent redisComponent;

    private final UserInfoService userInfoService;

    private final FileInfoService fileInfoService;
    private final FileDeliveryFacade fileDeliveryFacade;
    private final FileDownloadService fileDownloadService;
    private final FileAccessService fileAccessService;

    @GetMapping("/getSysSettings")
    public ResponseVo<SysSettingsDto> getSysSettings() {
        return ResponseVo.ok(redisComponent.getSysSettingsDto());
    }

    @PostMapping("/saveSysSettings")
    public ResponseVo<Void> saveSysSettings(
            @NotBlank String registerEmailTitle,
            @NotBlank String registerEmailContent,
            @NotNull Integer userInitTotalSpace) {
        SysSettingsDto sysSettingsDto = new SysSettingsDto();
        sysSettingsDto.setRegisterEmailTitle(registerEmailTitle);
        sysSettingsDto.setRegisterEmailContent(registerEmailContent);
        sysSettingsDto.setUserInitTotalSpace(userInitTotalSpace);
        redisComponent.saveSysSettingsDto(sysSettingsDto);
        return ResponseVo.ok(null);
    }

    @GetMapping("/loadUserList")
    public ResponseVo<PaginationResultVo<UserInfoVo>> loadUserList(UserInfoQuery query) {
        return ResponseVo.ok(userInfoService.pageUserList(LoginUser.currentUserId(), query));
    }


    @RequestMapping("/updateUserStatus")
    public ResponseVo<Void> updateUserStatus(@NotBlank @RequestParam("userId") String targetId, @NotNull Integer status) {
        userInfoService.updateUserStatus(LoginUser.currentUserId(), targetId, status);
        return ResponseVo.ok(null);
    }

    @PostMapping("/clearUserFiles")
    public ResponseVo<Void> clearUserFiles(@NotBlank @RequestParam("userId") String targetId) {
        userInfoService.clearUserFiles(LoginUser.currentUserId(), targetId);
        return ResponseVo.ok();
    }

    @PostMapping("/addUserTotalSpace")
    public ResponseVo<Void> addUserTotalSpace(@NotBlank @RequestParam("userId") String targetId,
                                              @NotNull Integer newSpace) {
        userInfoService.addUserTotalSpace(LoginUser.currentUserId(), targetId, newSpace);
        return ResponseVo.ok();
    }

    @GetMapping("/loadFileList")
    public ResponseVo<PaginationResultVo<FileInfoVo>> loadDataList(FileInfoQuery query) {
        return ResponseVo.ok(fileInfoService.pageUserFiles(query));
    }

    @GetMapping("/getFolderInfo")
    public ResponseVo<List<FolderVo>> getFolderInfo(@NotBlank String path) {
        return ResponseVo.ok(fileInfoService.getFolderInfoVoList(path, null));
    }

    @StreamResponse
    @GetMapping("/getFile/{userId}/{fileId}")
    public void getFile(HttpServletRequest request,
                        HttpServletResponse response,
                        @PathVariable @NotBlank String userId,
                        @PathVariable @NotBlank String fileId) {
        FileReadResourceDto res = fileAccessService.openForRead(fileId, userId);
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @StreamResponse
    @GetMapping("/ts/getVideoInfo/{userId}/{fileId}")
    public void getVideoInfo(HttpServletRequest request,
                             HttpServletResponse response,
                             @PathVariable @NotBlank String userId,
                             @PathVariable @NotBlank String fileId) {
        FileReadResourceDto res = fileAccessService.openForRead(fileId, userId);
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @PostMapping("/createDownloadUrl/{userId}")
    public ResponseVo<String> createDownloadUrl(
            @PathVariable @NotBlank String userId,
            @NotBlank String fileIds) {
        return ResponseVo.ok(fileDownloadService.createDownloadUrl(fileIds, userId));
    }

    @StreamResponse
    @GetMapping("/download/{code}")
    public void download(HttpServletRequest request,
                         HttpServletResponse response,
                         @PathVariable("code") @NotBlank String code) {
        fileDeliveryFacade.download(request, response, code);
    }

    @PostMapping("/delFile")
    public ResponseVo<Void> delFile(@RequestParam @NotBlank String fileIdAndUserIds) {
        fileInfoService.delFilesAdmin(fileIdAndUserIds);
        return ResponseVo.ok();
    }
}
