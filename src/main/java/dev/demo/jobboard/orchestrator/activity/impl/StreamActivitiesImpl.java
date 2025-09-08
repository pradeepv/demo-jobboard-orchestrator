package dev.demo.jobboard.orchestrator.activity.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;

import java.util.Map;

public class StreamActivitiesImpl implements StreamActivities {

  private final SseEventBus bus;
  private final ObjectMapper mapper = new ObjectMapper();

  public StreamActivitiesImpl(SseEventBus bus) {
    this.bus = bus;
  }

  @Override
  public void emit(String channel, String requestId, String type, String jsonPayload) {
    try {
      // Expecting JSON payload; convert to Map so SseEventBus can serialize as JSON cleanly
      Map<String, Object> payload = mapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});
      bus.publish(channel, type, payload);
    } catch (Exception e) {
      // As a fallback, send raw text payload
      bus.publish(channel, type, Map.of(
          "error", "stream-emit-json-parse-failed",
          "raw", jsonPayload,
          "requestId", requestId
      ));
    }
  }
}