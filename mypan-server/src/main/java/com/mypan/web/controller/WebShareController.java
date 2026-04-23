package com.mypan.web.controller;

import com.mypan.common.annotation.OptionalLogin;
import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.response.ResponseVo;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.infra.security.share.ShareAccessCookieCodec;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.service.file.access.FileAccessService;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.download.FileDownloadService;
import com.mypan.service.share.ShareInfoService;
import com.mypan.web.dto.response.AudioMetaVo;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import com.mypan.web.dto.response.share.ShareInfoVo;
import com.mypan.web.support.FileDeliveryFacade;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.util.List;

@RestController
@RequestMapping("/showShare")
@RequiredArgsConstructor
public class WebShareController {

    private final ShareInfoService shareInfoService;
    private final RedisComponent redisComponent;
    private final FileInfoService fileInfoService;
    private final FileDeliveryFacade fileDeliveryFacade;
    private final FileDownloadService fileDownloadService;
    private final FileAccessService fileAccessService;

    @PostMapping("/checkShareCode")
    public ResponseVo<Void> checkShareCode(HttpServletResponse resp,
                                           @NotBlank String shareId,
                                           @NotBlank String code,
                                           @CookieValue(name = Constants.COOKIE_SHARE_ACCESS, required = false) String oldVal) {
        ShareAccessDto dto = shareInfoService.checkShareCode(shareId, code);
        String accessKey = StringTools.getRandomString(Constants.SHARE_ACCESS_KEY_LENGTH);
        redisComponent.saveShareAccess(shareId, accessKey, dto);

        // upsert：把当前 shareId 的凭证放到最前，并最多保留 10 条
        String newVal = ShareAccessCookieCodec.upsert(oldVal, shareId, accessKey, Constants.COOKIE_SHARE_ID_MAX_NUM);
        Cookie cookie = new Cookie(Constants.COOKIE_SHARE_ACCESS, newVal);
        cookie.setPath("/");
        cookie.setMaxAge((int) Constants.REDIS_TIME_UNIT_SHARE_ACCESS.toSeconds(Constants.REDIS_TTL_SHARE_ACCESS));
        cookie.setHttpOnly(true);
        resp.addCookie(cookie);
        return ResponseVo.ok();
    }

    @GetMapping("/getShareLoginInfo")
    @OptionalLogin
    public ResponseVo<ShareInfoVo> getShareLoginInfo(HttpServletRequest req,
                                                     HttpServletResponse resp,
                                                     @NotBlank String shareId) {
        ShareAccessDto access = resolveShareAccess(shareId, req, resp, false);
        if (access == null) return ResponseVo.ok(null);
        ShareInfoVo vo = shareInfoService.getShareInfoVo(shareId);
        LoginUser loginUser = LoginUser.current();
        vo.setCurrentUser(loginUser != null && loginUser.getUserId().equals(access.getShareUserId()));
        return ResponseVo.ok(vo);
    }

    @GetMapping("/getShareInfo")
    public ResponseVo<ShareInfoVo> getShareInfo(@NotBlank String shareId) {
        return ResponseVo.ok(shareInfoService.getShareInfoVo(shareId));
    }

    @GetMapping("/loadFileList")
    public ResponseVo<PaginationResultVo<FileInfoVo>> loadFileList(HttpServletRequest req,
                                                                   HttpServletResponse resp,
                                                                   @NotBlank String shareId,
                                                                   String filePid,
                                                                   Integer pageNo,
                                                                   Integer pageSize) {
        ShareAccessDto access = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);  // 实时校验分享（存在/未删/用户可用/未过期）
        return ResponseVo.ok(fileInfoService.pageShareFiles(pageNo, pageSize, filePid, access));
    }

    @GetMapping("/getFolderInfo")
    public ResponseVo<List<FolderVo>> getFolderInfo(HttpServletRequest req,
                                                    HttpServletResponse resp,
                                                    @NotBlank String shareId,
                                                    @NotBlank String path) {
        ShareAccessDto dto = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);
        return ResponseVo.ok(fileInfoService.getFolderInfoVoList(path, dto.getShareUserId()));
    }

    @GetMapping("/getFile/{shareId}/{fileId}")
    public void getFile(HttpServletRequest req,
                        HttpServletResponse resp,
                        @PathVariable @NotBlank String shareId,
                        @PathVariable @NotBlank String fileId) {
        ShareAccessDto dto = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);
        FileReadResourceDto res = fileAccessService.openForRead(fileId, dto.getShareUserId());
        fileDeliveryFacade.writeResource(req, resp, res);
    }

    @GetMapping("/getImage/{shareId}/{month}/{userId}/{imageName}")
    public void getImage(HttpServletRequest req,
                         HttpServletResponse resp,
                         @PathVariable @NotBlank String shareId,
                         @PathVariable @NotBlank String month,
                         @PathVariable @NotBlank String userId,
                         @PathVariable @NotBlank String imageName,
                         @RequestParam(defaultValue = "thumb") String mode) {
        resolveShareAccess(shareId, req, resp, true);
        if ("preview".equalsIgnoreCase(mode)) {
            // 预览不走强缓存
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("Pragma", "no-cache");
            resp.setDateHeader("Expires", 0);
        } else { // 缩略图：允许强缓存
            resp.setHeader("Cache-Control", "max-age=2592000");
        }
        FileReadResourceDto res = fileAccessService.openThumbnailForRead(month + "/" + userId, imageName);
        fileDeliveryFacade.writeResource(req, resp, res);
    }

    @GetMapping("/ts/getVideoInfo/{shareId}/{fileId}")
    public void getVideoInfo(HttpServletRequest req,
                             HttpServletResponse resp,
                             @PathVariable @NotBlank String shareId,
                             @PathVariable @NotBlank String fileId) {
        ShareAccessDto dto = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);
        FileReadResourceDto res = fileAccessService.openForRead(fileId, dto.getShareUserId());
        fileDeliveryFacade.writeResource(req, resp, res);
    }

    @GetMapping("/audioMeta/{shareId}/{fileId}")
    public ResponseVo<AudioMetaVo> audioMetaShare(@PathVariable String shareId,
                                                  @PathVariable String fileId,
                                                  HttpServletRequest req,
                                                  HttpServletResponse resp) {
        ShareAccessDto dto = resolveShareAccess(shareId, req, resp, true);
        return ResponseVo.ok( fileDeliveryFacade.getAudioMeta(fileId, dto.getShareUserId()));
    }

    @PostMapping("/createDownloadUrl/{shareId}")
    public ResponseVo<String> createDownloadUrl(HttpServletRequest req,
                                                HttpServletResponse resp,
                                                @PathVariable @NotBlank String shareId,
                                                @NotBlank String fileIds) {
        ShareAccessDto dto = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);
        return ResponseVo.ok(fileDownloadService.createDownloadUrl(fileIds, dto.getShareUserId()));
    }

    @GetMapping("/download/{code}")
    public void download(HttpServletRequest req,
                         HttpServletResponse resp,
                         @PathVariable @NotBlank String code) throws Exception {
        fileDeliveryFacade.download(req, resp, code);
    }

    @PostMapping("/saveShare")
    @RequiresLogin
    public ResponseVo<Void> saveShare(HttpServletRequest req,
                                HttpServletResponse resp,
                                @NotBlank String shareId,
                                @NotBlank String shareFileIds,
                                @NotBlank String myFolderId) {
        ShareAccessDto access = resolveShareAccess(shareId, req, resp, true);
        shareInfoService.getShareInfoVo(shareId);
        fileInfoService.saveShareFiles(
                access.getFileId(),
                shareFileIds,
                myFolderId,
                access.getShareUserId(),
                LoginUser.currentUserId()
        );
        return ResponseVo.ok();
    }

    /**
     * 解析当前请求是否已获得「分享访问权限」（即是否已通过提取码校验）。
     *
     * <p>
     * 本方法负责从 Cookie 中读取分享访问凭证（share_access），
     * 并结合 Redis 校验该凭证是否仍然有效，用于判断当前请求是否
     * 已经「解锁」指定的分享。
     * </p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li><b>required = true</b>：
     *       用于必须已解锁的接口（如：loadFileList / 下载 / 预览 / 转存）。
     *       未解锁或凭证失效时会直接抛出异常。</li>
     *   <li><b>required = false</b>：
     *       用于 getShareLoginInfo 接口，
     *       若未解锁则返回 {@code null}，由前端跳转到输入提取码页面。</li>
     * </ul>
     *
     * <h3>校验逻辑</h3>
     * <ol>
     *   <li>从请求 Cookie 中读取 {@code share_access}。</li>
     *   <li>解析出当前 shareId 对应的 accessKey。</li>
     *   <li>使用 {@code shareId + accessKey} 查询 Redis，
     *       判断该访问凭证是否仍然存在且有效。</li>
     *   <li>若 Redis 未命中，则视为「解锁态已失效」：
     *       <ul>
     *         <li>清理 Cookie 中的僵尸条目；</li>
     *         <li>根据 {@code required} 参数决定抛异常或返回 {@code null}。</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <h3>异常说明</h3>
     * <ul>
     *   <li>当 {@code required = true} 且未解锁时，
     *       抛出 {@link BusinessException}，错误码为 {@code BAD_REQUEST}，
     *       表示当前请求前置条件不满足（需要重新输入提取码）。</li>
     *   <li>当 {@code required = false} 且未解锁时，返回 {@code null}。</li>
     * </ul>
     *
     * <p>
     * <b>注意：</b>本方法 <b>只负责校验是否已解锁</b>，
     * 不校验分享是否已过期、被删除或分享者是否被禁用；
     * 这些校验应由 {@code getShareInfoVo / getShareInfoDto} 统一处理，
     * 以保证分享状态判断的一致性。
     * </p>
     *
     * @param shareId 分享 ID
     * @param req 当前 HTTP 请求
     * @param resp 当前 HTTP 响应（用于清理/更新 Cookie）
     * @param required 是否要求必须已解锁
     * @return 已解锁时返回 {@link ShareAccessDto}；未解锁且 required=false 时返回 {@code null}
     * @throws BusinessException 当 required=true 且未解锁时抛出
     */
    private ShareAccessDto resolveShareAccess(String shareId,
                                              HttpServletRequest req,
                                              HttpServletResponse resp,
                                              boolean required) {
        Cookie oldCookie = WebUtils.getCookie(req, Constants.COOKIE_SHARE_ACCESS);
        String cookieVal = oldCookie == null ? null : oldCookie.getValue();
        String accessKey = ShareAccessCookieCodec.getAccessKey(cookieVal, shareId);

        if (accessKey == null) {
            if (required) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            return null;
        }

        ShareAccessDto access = redisComponent.getShareAccess(shareId, accessKey);
        if (access == null) {
            // 清理僵尸条目
            String newVal = ShareAccessCookieCodec.remove(cookieVal, shareId, 10);
            Cookie newCookie;
            if (newVal.isBlank()) {
                newCookie = new Cookie(Constants.COOKIE_SHARE_ACCESS, "");
                newCookie.setMaxAge(0);
            } else {
                newCookie = new Cookie(Constants.COOKIE_SHARE_ACCESS, newVal);
                newCookie.setMaxAge((int) Constants.REDIS_TIME_UNIT_SHARE_ACCESS.toSeconds(Constants.REDIS_TTL_SHARE_ACCESS));
            }
            newCookie.setPath("/");
            newCookie.setHttpOnly(true);
            resp.addCookie(newCookie);

            if (required) throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            return null;
        }
        return access;
    }
}
