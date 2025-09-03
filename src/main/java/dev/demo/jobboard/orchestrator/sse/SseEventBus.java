package dev.demo.jobboard.orchestrator.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEventBus {
    private static final Logger log = LoggerFactory.getLogger(SseEventBus.class);

    // channel -> subscribers
    private final Map<String, Set<SseEmitter>> channels = new ConcurrentHashMap<>();

    public SseEmitter addSubscriber(String channel) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        channels.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completion for channel={}", channel);
            remove(channel, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for channel={}", channel);
            remove(channel, emitter);
        });
        emitter.onError((ex) -> {
            log.debug("SSE error for channel={}, ex={}", channel, ex.toString());
            remove(channel, emitter);
        });

        // Immediate hello to verify connection
        try {
            log.debug("SSE send: channel={} event=connected", channel);
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("ok", true), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.debug("SSE initial send failed, removing subscriber. channel={} err={}", channel, e.toString());
            remove(channel, emitter);
        }
        return emitter;
    }

    public void publish(String channel, String eventName, Object payload) {
        var subs = channels.get(channel);
        if (subs == null || subs.isEmpty()) {
            log.debug("SSE publish dropped: no subscribers. channel={} event={}", channel, eventName);
            return;
        }
        for (SseEmitter em : subs) {
            try {
                log.debug("SSE send: channel={} event={} payloadType={}", channel, eventName,
                    payload == null ? "null" : payload.getClass().getSimpleName());
                em.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.debug("SSE send failed, removing subscriber. channel={} err={}", channel, e.toString());
                remove(channel, em);
            }
        }
    }

    public void complete(String channel, String requestId) {
        var subs = channels.remove(channel);
        if (subs == null) {
            log.debug("SSE complete dropped: no subscribers. channel={} requestId={}", channel, requestId);
            return;
        }
        for (SseEmitter em : subs) {
            try {
                log.debug("SSE complete: channel={} requestId={}", channel, requestId);
                em.send(SseEmitter.event().name("done").data(Map.of("requestId", requestId)));
            } catch (IOException ignored) {
            } finally {
                em.complete();
            }
        }
    }

    // Keepalive to prevent proxy timeouts
    @Scheduled(fixedRate = 15000)
    public void keepAlive() {
        channels.forEach((ch, subs) -> {
            for (SseEmitter em : subs) {
                try {
                    em.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis())));
                } catch (IOException e) {
                    remove(ch, em);
                }
            }
        });
    }

    private void remove(String channel, SseEmitter emitter) {
        var subs = channels.get(channel);
        if (subs != null) {
            subs.remove(emitter);
            if (subs.isEmpty()) {
                channels.remove(channel);
            }
        }
    }
}