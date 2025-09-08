package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.NotifyActivities;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class NotifyActivitiesImpl implements NotifyActivities {

  private final RestTemplate http = new RestTemplate();
  private final String baseUrl;

  public NotifyActivitiesImpl(String baseUrl) {
    // e.g., "http://localhost:8082"
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  @Override
  public void sourceComplete(String requestId, String source) {
    try {
      http.postForEntity(
          baseUrl + "/api/crawl/complete-source",
          Map.of("requestId", requestId, "source", source),
          Map.class
      );
    } catch (Exception ignored) {
      // Optionally log; failures to notify won't break the workflow
    }
  }
}