package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.constants.Constants;
import com.mypan.common.constants.VerifyRegex;
import com.mypan.common.response.ResponseVo;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.infra.security.jwt.JwtProperties;
import com.mypan.infra.security.jwt.JwtUtils;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.infra.security.session.SessionProperties;
import com.mypan.service.avatar.AvatarService;
import com.mypan.service.dto.UserSpaceDto;
import com.mypan.service.dto.responseWrite.FileReadResourceDto;
import com.mypan.service.user.EmailCodeService;
import com.mypan.service.user.UserInfoService;
import com.mypan.web.dto.request.RegisterDto;
import com.mypan.web.dto.request.ResetPwdDto;
import com.mypan.web.dto.request.SendEmailCodeDto;
import com.mypan.web.dto.response.user.UserProfileVo;
import com.mypan.web.support.FileDeliveryFacade;
import com.pig4cloud.captcha.SpecCaptcha;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final EmailCodeService emailCodeService;
    private final UserInfoService userInfoService;
    private final AppProperties appProperties;
    private final RedisUtils redisUtils;
    private final RedisComponent redisComponent;
    private final JwtUtils jwtUtils;
    private final AvatarService avatarService;
    private final SessionProperties sessionProperties;
    private final JwtProperties jwtProperties;
    private final FileDeliveryFacade fileDeliveryFacade;

    @GetMapping("/checkCode")
    public ResponseVo<Map<String, String>> checkCode(Integer type) {
        SpecCaptcha captcha = new SpecCaptcha(130, 38, Constants.CAPTCHA_LENGTH);
        String code = captcha.text();

        String token = UUID.randomUUID().toString();
        String keyPrefix = (type == null || type == 0)
                ? Constants.REDIS_KEY_CAPTCHA
                : Constants.REDIS_KEY_CAPTCHA_FOR_EMAIL;
        String redisKey = keyPrefix + token;

        redisComponent.saveCaptcha(redisKey, code);

        Map<String, String> result = new HashMap<>();
        result.put("checkCode", captcha.toBase64());
        result.put("checkCodeKey", token);
        return ResponseVo.ok(result);
    }

    @PostMapping("/sendEmailCode")
    public ResponseVo<Void> sendEmailCode(@RequestBody @Valid SendEmailCodeDto dto) {
        validateAndClearCheckCode(
                Constants.REDIS_KEY_CAPTCHA_FOR_EMAIL,
                dto.getCheckCodeKey(),
                dto.getCheckCode()
        );
        emailCodeService.sendEmail(dto.getEmail(), dto.getType());
        return ResponseVo.ok();
    }

    @PostMapping("/register")
    public ResponseVo<Void> register(@RequestBody @Valid RegisterDto dto) {
        validateAndClearCheckCode(
                Constants.REDIS_KEY_CAPTCHA,
                dto.getCheckCodeKey(),
                dto.getCheckCode()
        );
        userInfoService.register(
                dto.getEmail(),
                dto.getNickname(),
                dto.getPassword(),
                dto.getEmailCode()
        );
        return ResponseVo.ok();
    }

    @PostMapping("/login")
    public ResponseVo<Void> login(
            HttpServletResponse response,
            @NotBlank(message = "Email cannot be blank.") String email,
            @NotBlank(message = "Password cannot be blank.") String password,
            @NotBlank(message = "Captcha cannot be blank.") String checkCode,
            @NotBlank(message = "Captcha key cannot be blank.") String checkCodeKey
    ) {
        validateAndClearCheckCode(Constants.REDIS_KEY_CAPTCHA, checkCodeKey, checkCode);
        LoginUser loginUser = userInfoService.login(email, password);
        String token = jwtUtils.createToken(loginUser);
        // 写入登录态（滑动过期用它来判断是否仍在线）
        redisComponent.saveLoginStatus(loginUser.getUserId(), sessionProperties.getTtlMillis());

        Cookie cookie = new Cookie("token", token);
        cookie.setMaxAge((int) (jwtProperties.getExpireMillis() / 1000));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ResponseVo.ok();
    }

    @PostMapping("/resetPwd")
    public ResponseVo<Void> resetPwd(@RequestBody @Valid ResetPwdDto dto) {
        validateAndClearCheckCode(
                Constants.REDIS_KEY_CAPTCHA,
                dto.getCheckCodeKey(),
                dto.getCheckCode()
        );
        userInfoService.resetPwd(
                dto.getEmail(),
                dto.getPassword(),
                dto.getEmailCode()
        );
        return ResponseVo.ok();
    }

    @GetMapping("/getAvatar/{userId}")
    public void getAvatar(HttpServletRequest request,
                          HttpServletResponse response,
                          @PathVariable @NotBlank String userId) {
        FileReadResourceDto res = avatarService.openAvatarForRead(userId);
        response.setHeader("Cache-Control", "max-age=2592000");
        fileDeliveryFacade.writeResource(request, response, res);
    }

    @GetMapping("/getUserInfo")
    @RequiresLogin
    public ResponseVo<UserProfileVo> getUserInfo() {
        return ResponseVo.ok(userInfoService.getUserProfileVo(LoginUser.currentUserId()));
    }


    @GetMapping("/getUsedSpace")
    @RequiresLogin
    public ResponseVo<UserSpaceDto> getUserSpaceInfo() {
        return ResponseVo.ok(redisComponent.getUserSpaceInfo(LoginUser.currentUserId()));
    }

    @PostMapping("/auth/logout")
    @RequiresLogin
    public ResponseVo<Void> logout(HttpServletResponse response) {
        redisUtils.delete(Constants.REDIS_KEY_LOGIN_USER + LoginUser.currentUserId());
        Cookie cleanCookie = new Cookie("token", "");
        cleanCookie.setPath("/");
        cleanCookie.setMaxAge(0);
        cleanCookie.setHttpOnly(true);
        response.addCookie(cleanCookie);
        return ResponseVo.ok(null);
    }

    @PostMapping("/updateUserAvatar")
    @RequiresLogin
    public ResponseVo<Void> updateUserAvatar(@NotNull MultipartFile avatar) {
        String userId = LoginUser.currentUserId();
        avatarService.saveAvatar(userId, avatar);
        userInfoService.updateQqAvatarByUserId("", userId);
        redisUtils.delete(Constants.REDIS_KEY_USER_PROFILE + userId);
        return ResponseVo.ok();
    }

    @PostMapping("/updatePassword")
    @RequiresLogin
    public ResponseVo<Void> updatePassword(HttpServletResponse response,
            @Size(min = 8, max = 18) @Pattern(regexp = VerifyRegex.PASSWORD)
            String oldPassword,
            @Size(min = 8, max = 18) @Pattern(regexp = VerifyRegex.PASSWORD)
            String newPassword
    ) {
        userInfoService.updatePassword(oldPassword, newPassword, LoginUser.currentUserId());
        // 改密成功：立刻下线 + 拉黑旧 token + 清 cookie
        redisUtils.delete(Constants.REDIS_KEY_LOGIN_USER + LoginUser.currentUserId());
        Cookie cleanCookie = new Cookie("token", "");
        cleanCookie.setPath("/");
        cleanCookie.setMaxAge(0);
        cleanCookie.setHttpOnly(true);
        response.addCookie(cleanCookie);
        return ResponseVo.ok(null);
    }

    @GetMapping("/qqlogin")
    public ResponseVo<String> qqlogin(String callbackUrl) {
        String state = StringTools.getRandomString(Constants.QQ_LOGIN_STATE_LENGTH);
        if (StringUtils.hasText(callbackUrl))
            redisComponent.saveQqCallbackUrl(state, callbackUrl);
        String url = appProperties.getQq().getUrlAuthorization()
                .formatted(
                        appProperties.getQq().getAppId(),
                        URLEncoder.encode(
                                appProperties.getQq().getUrlRedirect(),
                                StandardCharsets.UTF_8),
                        state
                );
        return ResponseVo.ok(url);
    }

    @GetMapping("/qqlogin/callback")
    public ResponseVo<Map<String, Object>> qqLoginCallback(@NotBlank String code, @NotBlank String state) {
        LoginUser loginUser = userInfoService.qqLogin(code);
        String token = jwtUtils.createToken(loginUser);
        Map<String, Object> result = new HashMap<>();
        result.put("callbackUrl", redisUtils.get(Constants.REDIS_KEY_QQ_LOGIN_STATE + state));
        result.put("token", token);
        return ResponseVo.ok(result);
    }

    private void validateAndClearCheckCode(String redisKeyPrefix, String checkCodeKey, String checkCode) {
        String redisKey = redisKeyPrefix + checkCodeKey;
        emailCodeService.checkCode(redisKey, checkCode, true);
        redisUtils.delete(redisKey);
    }
}
