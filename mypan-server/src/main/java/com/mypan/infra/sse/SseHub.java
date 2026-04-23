package com.mypan.infra.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseHub {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);

        // 如果同一个用户已有连接，先踢掉，避免无限叠加
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("hello").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void pushToUser(String userId, TranscodeSseEvent event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name("transcode").data(event));
        } catch (Exception ex) {
            remove(userId, emitter);
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        emitters.remove(userId, emitter); // 只在 map 里还是这个 emitter 时才删
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            try {
                e.getValue().send(SseEmitter.event().name("heartbeat").data("1"));
            } catch (Exception ex) {
                remove(e.getKey(), e.getValue());
            }
        }
    }
}
