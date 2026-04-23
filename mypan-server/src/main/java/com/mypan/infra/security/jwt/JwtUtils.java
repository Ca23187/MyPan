package com.mypan.infra.security.jwt;

import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    private final JwtProperties props;
    private final SecretKey secretKey;

    public JwtUtils(JwtProperties props) {
        this.props = props;
        this.secretKey = props.getSecretKey();
    }

    /**
     * 生成 JWT token（裸 token，不带任何前缀）
     */
    public String createToken(LoginUser loginUser) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + props.getExpireMillis());

        JwtBuilder builder = Jwts.builder()
                .setSubject(props.getSubject())
                .setIssuer(props.getIssuer())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("userId", loginUser.getUserId());

        return builder
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token
     */
    public Claims parseToken(String token) {
        if (!StringUtils.hasText(token))
            throw new BusinessException(ResponseCodeEnum.NOT_LOGGED_IN);
        try {
            JwtParser parser = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .setAllowedClockSkewSeconds(props.getClockSkewSeconds())
                    .build();

            return parser.parseClaimsJws(token.trim()).getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResponseCodeEnum.LOGIN_TIMEOUT); // token 过期
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResponseCodeEnum.TOKEN_INVALID); // 非法 token
        }
    }

    /**
     * 获取 token 剩余过期的毫秒数
     */
    public long getRemainingMillis(String token) {
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();

        long nowMillis = System.currentTimeMillis();
        long expMillis = expiration.getTime();

        long diff = expMillis - nowMillis;
        return Math.max(diff, 0);
    }

    /**
     * 静默校验：只返回 true/false，不抛业务异常
     */
    public boolean validateTokenSilently(String token) {
        try {
            parseToken(token);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

}