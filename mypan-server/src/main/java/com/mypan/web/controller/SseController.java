package com.mypan.web.controller;

import com.mypan.common.annotation.RequiresLogin;
import com.mypan.infra.security.jwt.LoginUser;
import com.mypan.infra.sse.SseHub;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiresLogin
@RequiredArgsConstructor
public class SseController {

    private final SseHub sseHub;

    @GetMapping(value = "/sse/transcode", produces = "text/event-stream")
    public SseEmitter transcode(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // 如果你前面有 nginx，且开启了 proxy_buffering，建议：
        response.setHeader("X-Accel-Buffering", "no");

        return sseHub.subscribe(LoginUser.currentUserId());
    }
}
