package com.mypan.infra.security.session;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.infra.security.jwt.JwtUtils;
import com.mypan.infra.security.jwt.LoginUser;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AuthSessionService {

    private final RedisUtils redisUtils;
    private final SessionProperties sessionProperties;
    private final JwtUtils jwtUtils;

    public LoginUser authenticateToken(String token) {
        if (!StringUtils.hasText(token))
            throw new BusinessException(ResponseCodeEnum.LOGIN_TIMEOUT);
        token = token.trim();

        // JWT 校验（过期直接抛）
        Claims claims = jwtUtils.parseToken(token);
        LoginUser loginUser = LoginUser.of((String) claims.get("userId"));

        // Redis 会话必须存在（key = userId）
        String loginKey = Constants.REDIS_KEY_LOGIN_USER + loginUser.getUserId();
        if (redisUtils.get(loginKey) == null)
            throw new BusinessException(ResponseCodeEnum.LOGIN_TIMEOUT);

        return loginUser;
    }


    public String tryRefreshToken(String token, LoginUser loginUser) {
        String newToken = null;
        long remainingMillis = jwtUtils.getRemainingMillis(token);
        if (remainingMillis <= sessionProperties.getRenewThresholdMillis()) {
            // Redis 会话滑动续期
            String loginKey = Constants.REDIS_KEY_LOGIN_USER + loginUser.getUserId();
            redisUtils.expire(loginKey, sessionProperties.getTtlMillis(), TimeUnit.MILLISECONDS);
            // JWT 临期刷新
            newToken = jwtUtils.createToken(loginUser);
        }
        return newToken;
    }
}