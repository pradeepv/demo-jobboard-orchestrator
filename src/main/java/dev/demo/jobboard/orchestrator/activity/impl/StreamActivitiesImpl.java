package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * StreamActivities implementation that posts events back to the API.
 * Can work with either HTTP calls (for separate JVMs) or direct SseEventBus (for same JVM).
 */
public class StreamActivitiesImpl implements StreamActivities {

    private static final Logger log = LoggerFactory.getLogger(StreamActivitiesImpl.class);

    private final RestTemplate http = new RestTemplate();
    private final String baseUrl;
    private final SseEventBus sseEventBus;  // For direct event publishing when in same JVM

    public StreamActivitiesImpl(SseEventBus eventBus) {
        this.sseEventBus = eventBus;
        this.baseUrl = System.getProperty("api.baseUrl", "http://localhost:8082");
        log.info("StreamActivitiesImpl using API baseUrl={}, direct bus={}", this.baseUrl, this.sseEventBus != null);
    }

    @Override
    public void emit(String channel, String requestId, String type, String jsonPayload) {
        // Try direct SseEventBus first (for same JVM), fallback to HTTP (for separate JVMs)
        if (sseEventBus != null) {
            try {
                // Parse JSON payload to send as object
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Object parsedPayload = mapper.readValue(jsonPayload, Object.class);
                sseEventBus.publish(channel, type, parsedPayload);
                log.debug("StreamActivitiesImpl published via SseEventBus: channel={} requestId={} type={}", channel, requestId, type);
            } catch (Exception e) {
                log.warn("SseEventBus publish failed, falling back to HTTP: {}", e.toString());
                // Fallback to HTTP
                post(requestId, type, Map.of("raw", jsonPayload));
            }
        } else {
            post(requestId, type, Map.of("raw", jsonPayload));
        }
    }

    @Override
    public void emitObj(String channel, String requestId, String type, Map<String, Object> payload) {
        // Try direct SseEventBus first (for same JVM), fallback to HTTP (for separate JVMs)
        if (sseEventBus != null) {
            try {
                sseEventBus.publish(channel, type, payload);
                log.debug("StreamActivitiesImpl published via SseEventBus: channel={} requestId={} type={}", channel, requestId, type);
            } catch (Exception e) {
                log.warn("SseEventBus publish failed, falling back to HTTP: {}", e.toString());
                // Fallback to HTTP
                post(requestId, type, payload);
            }
        } else {
            post(requestId, type, payload);
        }
    }

    private void post(String requestId, String type, Map<String, Object> payload) {
        try {
            http.postForEntity(
                baseUrl + "/api/stream/ingest",
                Map.of(
                    "requestId", requestId,
                    "event", type,
                    "payload", payload
                ),
                Map.class
            );
            log.debug("StreamActivitiesImpl posted event via HTTP: requestId={} type={} payloadKeys={}", requestId, type, payload.keySet());
        } catch (Exception e) {
            log.warn("StreamActivitiesImpl HTTP emit failed: requestId={} type={} err={}", requestId, type, e.toString());
        }
    }
}