package com.mypan.web.interceptor;

import com.mypan.common.annotation.IgnoreLogin;
import com.mypan.common.annotation.OptionalLogin;
import com.mypan.common.annotation.RequiresLogin;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.config.AppProperties;
import com.mypan.infra.security.jwt.JwtProperties;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.infra.security.session.AuthSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final AppProperties appProperties;
    private final AuthSessionService authSessionService;
    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // 1. 只拦截 Controller 层方法
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 2. 检查方法 / 类上是否有注解
        // 方法上是否显式忽略登录
        boolean ignoreLogin = handlerMethod.hasMethodAnnotation(IgnoreLogin.class);

        boolean optionalLogin =
                handlerMethod.hasMethodAnnotation(OptionalLogin.class) ||
                        handlerMethod.getBeanType().isAnnotationPresent(OptionalLogin.class);

        boolean requiresLogin = !ignoreLogin && (
                        handlerMethod.hasMethodAnnotation(RequiresLogin.class) ||
                        handlerMethod.getBeanType().isAnnotationPresent(RequiresLogin.class));

        // 如果既不需要登录也不是可选登录，直接放行
        if (!requiresLogin && !optionalLogin) {
            return true;
        }

        // 3. 获取 token
        String token = null;
        Cookie cookie = WebUtils.getCookie(request, "token");
        if (cookie != null) {
            token = cookie.getValue();
        }

        // 4. 没 token：强制登录报错；可选登录放行
        if (!StringUtils.hasText(token)) {
            if (requiresLogin)  // 需要登录：必须有 token
                throw new BusinessException(ResponseCodeEnum.NOT_LOGGED_IN);
            return true;  // 可选登录：没有 token 直接放行
        }

        // 4. 有 token 则尝试校验 + 续期 + 写入 SecurityContext
        try {
            LoginUser loginUser = authSessionService.authenticateToken(token);
            String newToken = authSessionService.tryRefreshToken(token, loginUser);
            if (newToken != null) {
                Cookie newCookie = new Cookie("token", newToken);
                newCookie.setMaxAge((int) (jwtProperties.getExpireMillis() / 1000));
                newCookie.setPath("/");
                newCookie.setHttpOnly(true);
                response.addCookie(newCookie);
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    loginUser,
                    null,
                    Boolean.TRUE.equals(appProperties.getAdminIds().contains(loginUser.getUserId()))
                            ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            return true;
        } catch (BusinessException e) {
            // token 无效/过期/黑名单/redis session 不存在……
            if (requiresLogin) {
                throw e;
            }
            // optionalLogin：当游客放行，并确保上下文为空
            SecurityContextHolder.clearContext();
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception e) {
        SecurityContextHolder.clearContext();
    }
}
