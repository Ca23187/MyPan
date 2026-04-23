package com.mypan.service.user.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.UserStatusEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.http.OKHttpUtils;
import com.mypan.common.utils.json.JsonUtils;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.entity.QUserInfo;
import com.mypan.infra.jpa.entity.UserInfo;
import com.mypan.infra.jpa.querydsl.file.UserInfoQueryDsl;
import com.mypan.infra.jpa.querydsl.support.QueryDslUtils;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.infra.jpa.repository.UserInfoRepository;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.service.dto.QQInfoDto;
import com.mypan.service.dto.SysSettingsDto;
import com.mypan.service.user.EmailCodeService;
import com.mypan.service.user.UserInfoService;
import com.mypan.web.dto.query.UserInfoQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.user.UserInfoVo;
import com.mypan.web.dto.response.user.UserProfileVo;
import com.github.f4b6a3.ulid.UlidCreator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoRepository userInfoRepository;
    private final EmailCodeService emailCodeService;
    private final RedisComponent redisComponent;
    private final AppProperties appProperties;
    private final QueryDslUtils queryDslUtils;
    private final FileInfoRepository fileInfoRepository;
    private final RedisUtils redisUtils;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickname, String password, String emailCode) {
        UserInfo userInfo = userInfoRepository.findByEmailOrNickname(email, nickname);
        if (null != userInfo)
            if (email.equals(userInfo.getEmail()))
                throw new BusinessException(ResponseCodeEnum.EMAIL_ALREADY_EXISTS);
            else
                throw new BusinessException(ResponseCodeEnum.NICKNAME_ALREADY_EXISTS);
        //校验邮箱验证码
        emailCodeService.checkCode(Constants.REDIS_KEY_EMAIL_CODE + email, emailCode, false);

        userInfo = new UserInfo();
        userInfo.setUserId(UlidCreator.getUlid().toString());
        userInfo.setNickname(nickname);
        userInfo.setEmail(email);
        userInfo.setPassword(passwordEncoder.encode(password));
        userInfo.setStatus(UserStatusEnum.ACTIVE.getStatus());
        SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
        userInfo.setTotalSpace(sysSettingsDto.getUserInitTotalSpace() * Constants.MB);
        userInfo.setUsedSpace(0L);
        userInfoRepository.save(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginUser login(String email, String password) {
        UserInfo userInfo = userInfoRepository.findByEmail(email);
        if (null == userInfo || !passwordEncoder.matches(password, userInfo.getPassword()))
            throw new BusinessException("Incorrect username or password.");
        if (UserStatusEnum.DISABLED.getStatus().equals(userInfo.getStatus()))
            throw new BusinessException("Account has been disabled.");
        userInfo.setLastLoginAt(LocalDateTime.now());  // set 后无需手动 save，事务结束后 JPA 会自动提交
        return LoginUser.of(userInfo.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        UserInfo userInfo = userInfoRepository.findByEmail(email);
        if (null == userInfo)
            throw new BusinessException("The email account does not exist.");
        //校验邮箱验证码
        emailCodeService.checkCode(Constants.REDIS_KEY_EMAIL_CODE + email, emailCode, false);
        userInfo.setPassword(passwordEncoder.encode(password));
        userInfoRepository.save(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQqAvatarByUserId(String s, String userId) {
        userInfoRepository.updateQqAvatarByUserId(s, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(String oldPassword, String newPassword, String userId) {
        if (oldPassword.equals(newPassword))
            throw new BusinessException("New password cannot be the same as current password");
        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCodeEnum.BAD_REQUEST));
        String stored = user.getPassword();
        if (!passwordEncoder.matches(oldPassword, stored))
            throw new BusinessException("Current password is incorrect");
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginUser qqLogin(String code) {
        String accessToken = getQQAccessToken(code);
        String openId = getQQOpenId(accessToken);
        UserInfo user = userInfoRepository.findByQqOpenId(openId);
        if (null == user) {
            // 第一次用qq登录
            QQInfoDto qqInfo = getQQUserInfo(accessToken, openId);
            String nickname = buildUniqueNickName(qqInfo.getNickname(), openId);
            user = new UserInfo();
            user.setQqOpenId(openId);
            user.setNickname(nickname);
            user.setQqAvatar(null);
            user.setUserId(UlidCreator.getUlid().toString());
            user.setLastLoginAt(LocalDateTime.now());
            user.setStatus(UserStatusEnum.ACTIVE.getStatus());
            user.setUsedSpace(0L);
            user.setTotalSpace(redisComponent.getSysSettingsDto().getUserInitTotalSpace() * Constants.MB);
            userInfoRepository.save(user);
        } else user.setLastLoginAt(LocalDateTime.now());
        if (UserStatusEnum.DISABLED.getStatus().equals(user.getStatus()))
            throw new BusinessException("账号被禁用无法登录");
        return LoginUser.of(user.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaginationResultVo<UserInfoVo> pageUserList(String userId, UserInfoQuery query) {
        QUserInfo u = QUserInfo.userInfo;
        BooleanBuilder where = UserInfoQueryDsl.buildPredicate(query);

        // 管理员置顶逻辑下沉到 QueryDsl 层
        List<String> adminIds = appProperties.getAdminIds();
        List<OrderSpecifier<?>> orders = UserInfoQueryDsl.buildOrderSpecifiers(query, adminIds);

        return queryDslUtils.findPageByParam(
                u,
                where,
                query.getPageNo(),
                query.getPageSize(),
                orders,
                UserInfoVo.selectBase(u, adminIds)
        );
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String operatorId, String targetId, Integer status) {
        if (Objects.equals(operatorId, targetId))
            throw new BusinessException("You cannot disable yourself.");
        if (appProperties.getAdminIds().contains(targetId))
            throw new BusinessException("Administrators cannot disable each other.");
        if (Objects.equals(status, UserStatusEnum.DISABLED.getStatus())
                || Objects.equals(status, UserStatusEnum.ACTIVE.getStatus()))
            userInfoRepository.updateStatusByUserId(status, targetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearUserFiles(String operatorId, String targetId) {
        if (!Objects.equals(operatorId, targetId)
                && appProperties.getAdminIds().contains(targetId))
            throw new BusinessException("Administrators cannot clear each other's cloud storage.");
        fileInfoRepository.updateDelFlagByUserId(FileDelFlagEnum.DELETED.getFlag(), targetId);
        userInfoRepository.updateUsedSpaceByUserId(0L, targetId);
        redisUtils.delete(Constants.REDIS_KEY_USER_SPACE_INFO + targetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUserTotalSpace(String operatorId, String targetId, Integer newSpace) {
        if (!Objects.equals(operatorId, targetId)
                && appProperties.getAdminIds().contains(targetId))
            throw new BusinessException("Administrators cannot modify each other's total storage space.");
        Long space = newSpace * Constants.MB;
        int count = userInfoRepository.addTotalSpace(targetId, space);
        if (count == 0)
            throw new BusinessException("Total space cannot be less than used space or negative.");
        redisUtils.delete(Constants.REDIS_KEY_USER_SPACE_INFO + targetId);
    }

    @Override
    public UserProfileVo getUserProfileVo(String userId) {
        // 1) 读缓存
        UserProfileVo cached = redisComponent.getUserProfileVo(userId);
        if (cached != null) return cached;

        // 2) 查库
        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCodeEnum.BAD_REQUEST));

        UserProfileVo vo = new UserProfileVo();
        vo.setUserId(userId);
        vo.setNickname(user.getNickname());
        vo.setIsAdmin(appProperties.getAdminIds().contains(userId));

        // 3) 回填缓存
        redisComponent.saveUserProfileVo(userId, vo);
        return vo;
    }

    @Override
    public void addUsedSpace(String userId, long fileSize) {
        int count = userInfoRepository.addUsedSpace(userId, fileSize);
        if (count == 0)
            throw new BusinessException(ResponseCodeEnum.STORAGE_INSUFFICIENT);
        // 更新完直接删缓存，防并发
        redisUtils.delete(Constants.REDIS_KEY_USER_SPACE_INFO + userId);
    }

    private String getQQAccessToken(String code) {
        String accessToken = null;
        String url = appProperties.getQq().getUrlAccessToken().formatted(
                appProperties.getQq().getAppId(),
                appProperties.getQq().getAppKey(),
                code,
                URLEncoder.encode(appProperties.getQq().getUrlRedirect(), StandardCharsets.UTF_8));
        String tokenResult = OKHttpUtils.getRequest(url, null);
        if (tokenResult == null || tokenResult.contains(Constants.VIEW_OBJ_RESULT_KEY)) {
            log.error("获取qqToken失败:{}", tokenResult);
            throw new BusinessException("Failed to obtain QQ token.");
        }
        String[] params = tokenResult.split("&");
        for (String p : params) {
            if (p.contains("access_token")) {
                accessToken = p.split("=")[1];
                break;
            }
        }
        return accessToken;
    }


    private String getQQOpenId(String accessToken) {
        // 获取openId
        String url = appProperties.getQq().getUrlOpenId().formatted(accessToken);
        String openIDResult = OKHttpUtils.getRequest(url, null);
        String tmpJson = this.getQQResp(openIDResult);
        if (tmpJson == null) {
            log.error("调qq接口获取openID失败:tmpJson{}", tmpJson);
            throw new BusinessException("Failed to retrieve openID via QQ API");
        }
        Map jsonData = JsonUtils.toObj(tmpJson, Map.class);
        if (jsonData == null || jsonData.containsKey(Constants.VIEW_OBJ_RESULT_KEY)) {
            log.error("调qq接口获取openID失败:{}", jsonData);
            throw new BusinessException("Failed to retrieve openID via QQ API");
        }
        return String.valueOf(jsonData.get("openid"));
    }


    private QQInfoDto getQQUserInfo(String accessToken, String qqOpenId) {
        String url = appProperties.getQq().getUrlUserInfo().formatted(accessToken, appProperties.getQq().getAppId(), qqOpenId);
        String response = OKHttpUtils.getRequest(url, null);
        if (StringUtils.isNotBlank(response)) {
            QQInfoDto qqInfo = JsonUtils.toObj(response, QQInfoDto.class);
            if (qqInfo.getRet() != 0) {
                log.error("qqInfo:{}", response);
                throw new BusinessException("Anomaly detected while accessing QQ API to retrieve user information.");
            }
            return qqInfo;
        }
        throw new BusinessException("Anomaly detected while accessing QQ API to retrieve user information.");
    }

    private String getQQResp(String result) {
        if (StringUtils.isNotBlank(result)) {
            // 使用正则提取括号里的内容
            Pattern pattern = Pattern.compile("callback\\s*\\((.*)\\)"); // 捕获括号内内容
            Matcher matcher = pattern.matcher(result);
            if (matcher.find())
                return matcher.group(1).trim(); // 去掉前后空格
        }
        return null;
    }

    private String buildUniqueNickName(String nickname, String openId) {
        if (nickname == null)
            nickname = "QQ用户"; // 默认昵称
        int maxNickLength = 15; // 昵称最长 15 个字符
        int suffixLength = 5;   // openId 后 5 位作为后缀

        // 处理原始昵称长度，按 Unicode 字符截断，兼容 emoji
        int nickCodePoints = nickname.codePointCount(0, nickname.length());
        if (nickCodePoints > maxNickLength)
            nickname = nickname.substring(0, nickname.offsetByCodePoints(0, maxNickLength));

        // 获取 openId 后 5 位
        String suffix;
        if (openId.length() >= suffixLength)
            suffix = openId.substring(openId.length() - suffixLength);
        else
            suffix = openId; // openId 不足 5 位就全部使用
        return nickname + suffix;
    }
}
