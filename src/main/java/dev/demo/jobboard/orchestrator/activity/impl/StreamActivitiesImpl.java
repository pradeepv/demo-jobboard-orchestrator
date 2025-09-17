package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * StreamActivities implementation that posts events back to the API via HTTP.
 * This allows worker and API to run in separate JVMs and still deliver SSE events to clients.
 *
 * Configure API base URL via system property: -Dapi.baseUrl=http://localhost:8082
 */
public class StreamActivitiesImpl implements StreamActivities {

    private static final Logger log = LoggerFactory.getLogger(StreamActivitiesImpl.class);

    private final RestTemplate http = new RestTemplate();
    private final String baseUrl;

    // Keep the constructor signature if your TemporalConfig wires it with a bus; we ignore the bus in the worker.
    public StreamActivitiesImpl(Object unusedBus) {
        this.baseUrl = System.getProperty("api.baseUrl", "http://localhost:8082");
        log.info("StreamActivitiesImpl using API baseUrl={}", this.baseUrl);
    }

    @Override
    public void emit(String channel, String requestId, String type, String jsonPayload) {
        post(requestId, type, Map.of("raw", jsonPayload));
    }

    @Override
    public void emitObj(String channel, String requestId, String type, Map<String, Object> payload) {
        post(requestId, type, payload);
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
            log.debug("StreamActivitiesImpl posted event: requestId={} type={} payloadKeys={}", requestId, type, payload.keySet());
        } catch (Exception e) {
            log.warn("StreamActivitiesImpl HTTP emit failed: requestId={} type={} err={}", requestId, type, e.toString());
        }
    }
}