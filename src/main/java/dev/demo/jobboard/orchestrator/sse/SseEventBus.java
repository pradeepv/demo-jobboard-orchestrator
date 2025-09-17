package dev.demo.jobboard.orchestrator.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

public class SseEventBus {
    private static final Logger log = LoggerFactory.getLogger(SseEventBus.class);

    // channel -> subscribers
    private final ConcurrentMap<String, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "sse-heartbeats");
        t.setDaemon(true);
        return t;
    });

    private final Duration emitterTimeout;
    private final Duration heartbeatEvery;

    public SseEventBus() {
        this(Duration.ofMinutes(30), Duration.ofSeconds(20));
    }

    public SseEventBus(Duration emitterTimeout, Duration heartbeatEvery) {
        this.emitterTimeout = emitterTimeout;
        this.heartbeatEvery = heartbeatEvery;
    }

    public SseEmitter subscribe(String channel) {
        return subscribe(channel, null);
    }

    // Optional: pass HttpServletResponse from controller to set headers
    public SseEmitter subscribe(String channel, HttpServletResponse response) {
        Objects.requireNonNull(channel, "channel");

        if (response != null) {
            // Critical headers for SSE reliability
            response.setHeader("Cache-Control", "no-cache, no-transform");
            response.setHeader("Connection", "keep-alive");
            // If you have a reverse proxy, adding this can help:
            // response.setHeader("X-Accel-Buffering", "no"); // nginx
        }

        long timeoutMs = emitterTimeout.toMillis();
        SseEmitter emitter = new SseEmitter(timeoutMs <= 0 ? 0L : timeoutMs);

        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(emitter);

        AtomicLong lastBeat = new AtomicLong(System.currentTimeMillis());

        // Lifecycle
        emitter.onCompletion(() -> {
            remove(channel, emitter);
            log.info("SSE completion: channel={} subscribersNow={}", channel, size(channel));
        });
        emitter.onTimeout(() -> {
            remove(channel, emitter);
            log.info("SSE timeout: channel={} subscribersNow={}", channel, size(channel));
        });
        emitter.onError((ex) -> {
            remove(channel, emitter);
            log.debug("SSE error: channel={} err={}", channel, ex.toString());
        });

        // Initial "connected" event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("ok", true, "channel", channel, "ts", System.currentTimeMillis()))
                .reconnectTime(3000L));
        } catch (IOException e) {
            log.warn("Failed to send initial connected event: channel={}", channel, e);
        }

        // Schedule heartbeats as SSE comments
        ScheduledFuture<?> hb = heartbeats.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping " + System.currentTimeMillis()));
                lastBeat.set(System.currentTimeMillis());
            } catch (Exception e) {
                // client disconnected or network issue; cleanup will happen via onError
                remove(channel, emitter);
            }
        }, heartbeatEvery.toSeconds(), heartbeatEvery.toSeconds(), TimeUnit.SECONDS);

        // Cancel heartbeat when emitter completes
        emitter.onCompletion(() -> hb.cancel(false));
        emitter.onTimeout(() -> hb.cancel(false));
        emitter.onError((ex) -> hb.cancel(false));

        log.info("SSE subscribed: channel={} subscribersNow={}", channel, size(channel));
        return emitter;
    }

    public void publish(String channel, String eventName, Object payload) {
        Set<SseEmitter> subs = subscribers.get(channel);
        int subsCount = subs == null ? 0 : subs.size();
        log.info("SSE publish: channel={} event={} subs={}", channel, eventName, subsCount);
        if (subsCount == 0) {
            log.warn("SSE publish buffered but no subscribers currently attached. channel={} event={}", channel, eventName);
            return;
        }
        if (subs == null) return;

        for (SseEmitter emitter : subs) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(payload)
                );
            } catch (IOException ex) {
                log.warn("SSE send failed: channel={} event={} err={}", channel, eventName, ex.toString());
                remove(channel, emitter);
            }
        }
    }

    public void complete(String channel, String reason) {
        Set<SseEmitter> subs = subscribers.get(channel);
        if (subs == null || subs.isEmpty()) {
            log.info("SSE complete: no subscribers for channel={}", channel);
            return;
        }
        for (SseEmitter emitter : subs) {
            try {
                emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true, "reason", reason)));
                emitter.complete();
            } catch (IOException e) {
                remove(channel, emitter);
            }
        }
        subscribers.remove(channel);
    }

    private void remove(String channel, SseEmitter emitter) {
        Set<SseEmitter> subs = subscribers.get(channel);
        if (subs != null) {
            subs.remove(emitter);
            if (subs.isEmpty()) {
                subscribers.remove(channel);
            }
        }
    }

    private int size(String channel) {
        Set<SseEmitter> subs = subscribers.get(channel);
        return subs == null ? 0 : subs.size();
    }
}