package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.annotation.StreamResponse;
import com.mypan.common.response.ResponseVo;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.download.FileDownloadService;
import com.mypan.service.file.upload.FileUploadService;
import com.mypan.web.dto.query.FileInfoQuery;
import com.mypan.web.dto.request.UploadInitRequestDto;
import com.mypan.web.dto.response.AudioMetaVo;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import com.mypan.web.dto.response.upload.UploadSessionVo;
import com.mypan.web.support.FileDeliveryFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/file")
@RequiresLogin
@RequiredArgsConstructor
public class FileController {

    private final FileInfoService fileInfoService;
    private final FileDeliveryFacade fileDeliveryFacade;
    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;
    private final FileAccessService fileAccessService;

    @GetMapping("/loadDataList")
    public ResponseVo<PaginationResultVo<FileInfoVo>> loadDataList(FileInfoQuery query) {
        return ResponseVo.ok(fileInfoService.pageMyFiles(LoginUser.currentUserId(), query));
    }

    @PostMapping("/initUpload")
    public ResponseVo<UploadSessionVo> initUpload(@RequestBody UploadInitRequestDto req) {
        return ResponseVo.ok(fileUploadService.initUpload(LoginUser.currentUserId(), req));
    }

    @PostMapping("/uploadFile")
    public ResponseVo<UploadSessionVo> uploadFile(String fileId,
                                                  MultipartFile file,
                                                  @NotBlank String fileName,
                                                  @NotBlank String filePid,
                                                  @NotBlank String fileMd5,
                                                  @NotNull @Min(0) Integer chunkIndex,
                                                  @NotNull @Min(1) Integer chunks) {
        return ResponseVo.ok(fileUploadService.uploadFile(LoginUser.currentUserId(), fileId, file, fileName, filePid, fileMd5, chunkIndex, chunks));
    }

    @GetMapping("/resumeUpload")
    public ResponseVo<UploadSessionVo> resumeUpload(@NotBlank String fileId) {
        return ResponseVo.ok(fileUploadService.resumeUpload(LoginUser.currentUserId(), fileId));
    }

    @PostMapping("/abortUpload")
    public ResponseVo<UploadSessionVo> abortUpload(@NotBlank String fileId) {
        UploadSessionVo vo = fileUploadService.abortUpload(LoginUser.currentUserId(), fileId);
        return ResponseVo.ok(vo);
    }


    @StreamResponse
    @GetMapping("/getImage/{month}/{userId}/{imageName}")
    public void getImage(HttpServletRequest request,
                         HttpServletResponse response,
                         @PathVariable @NotBlank String month,
                         @PathVariable @NotBlank String userId,
                         @PathVariable @NotBlank String imageName,
                         @RequestParam(defaultValue = "thumb") String mode) {
        if ("preview".equalsIgnoreCase(mode)) {
            // 预览不走强缓存
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        } else { // 缩略图：允许强缓存
            response.setHeader("Cache-Control", "max-age=2592000");
        }
        FileReadResourceDto res = fileAccessService.openThumbnailForRead(month + "/" + userId, imageName);
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @StreamResponse
    @GetMapping("/ts/getVideoInfo/{fileId}")
    public void getVideoInfo(HttpServletRequest request,
                             HttpServletResponse response,
                             @PathVariable @NotBlank String fileId) {
        FileReadResourceDto res = fileAccessService.openForRead(fileId, LoginUser.currentUserId());
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @StreamResponse
    @GetMapping("/getFile/{fileId}")
    public void getFile(HttpServletRequest request,
                        HttpServletResponse response,
                        @PathVariable @NotBlank String fileId) {
        FileReadResourceDto res = fileAccessService.openForRead(fileId, LoginUser.currentUserId());
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @GetMapping("/audioMeta/{fileId}")
    public ResponseVo<AudioMetaVo> audioMeta(@PathVariable @NotBlank String fileId) {
        return ResponseVo.ok(fileDeliveryFacade.getAudioMeta(fileId, LoginUser.currentUserId()));
    }

    @PostMapping("/newFolder")
    public ResponseVo<FileInfo> newFolder(@NotBlank String filePid, @RequestParam("fileName") @NotBlank String folderName) {
        return ResponseVo.ok(fileInfoService.createFolder(filePid, LoginUser.currentUserId(), folderName));
    }

    @GetMapping("/getFolderInfo")
    public ResponseVo<List<FolderVo>> getFolderInfo(@NotBlank String path) {
        return ResponseVo.ok(fileInfoService.getFolderInfoVoList(path, LoginUser.currentUserId()));
    }

    @PostMapping("/rename")
    public ResponseVo<FileInfoVo> rename(@NotBlank String fileId, @NotBlank String fileName) {
        return ResponseVo.ok(fileInfoService.rename(fileId, LoginUser.currentUserId(), fileName));
    }

    @GetMapping("/loadAllFolder")
    public ResponseVo<List<FileInfoVo>> loadAllFolder(@NotBlank String filePid, String currentFileIds) {
        return ResponseVo.ok(fileInfoService.findMovableTargetFolders(LoginUser.currentUserId(), filePid, currentFileIds));
    }

    @PostMapping("/changeFileFolder")
    public ResponseVo<Void> changeFileFolder(@NotBlank String fileIds, @NotBlank String filePid) {
        fileInfoService.changeFileLocation(fileIds, filePid, LoginUser.currentUserId());
        return ResponseVo.ok();
    }

    @PostMapping("/createDownloadUrl")
    public ResponseVo<String> createDownloadUrl(@NotBlank String fileIds) {
        return ResponseVo.ok(fileDownloadService.createDownloadUrl(fileIds, LoginUser.currentUserId()));
    }

    @StreamResponse
    @GetMapping("/download/{code}")
    public void download(HttpServletRequest request,
                         HttpServletResponse response,
                         @PathVariable @NotBlank String code) {
        fileDeliveryFacade.download(request, response, code);
    }


    @PostMapping("/delFile")
    public ResponseVo<Void> delFile(@NotBlank String fileIds) {
        fileInfoService.moveFiles2RecycleBin(LoginUser.currentUserId(), fileIds);
        return ResponseVo.ok();
    }

    @GetMapping("/getFolderBreadcrumb")
    public ResponseVo<List<FolderVo>> getFolderBreadcrumb(@NotBlank String fileId) {
        return ResponseVo.ok(
                fileInfoService.getFolderBreadcrumb(LoginUser.currentUserId(), fileId)
        );
    }
}