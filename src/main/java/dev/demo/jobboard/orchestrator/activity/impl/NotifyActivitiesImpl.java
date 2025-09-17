package dev.demo.jobboard.orchestrator.activity.impl;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import dev.demo.jobboard.orchestrator.activity.NotifyActivities;

public class NotifyActivitiesImpl implements NotifyActivities {

  private static final Logger log = LoggerFactory.getLogger(NotifyActivitiesImpl.class);

  private final RestTemplate http = new RestTemplate();
  private final String baseUrl;

  public NotifyActivitiesImpl(String baseUrl) {
    // e.g., "http://localhost:8082"
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  @Override
  public void sourceComplete(String requestId, String source) {
    String path;
    if ("analysis".equalsIgnoreCase(source)) {
      // Route analysis completion to analysis endpoint so SSE can be closed for that request
      path = "/api/analysis/complete-source";
    } else {
      // Default to crawl completion for all other sources
      path = "/api/crawl/complete-source";
    }

    try {
      log.debug("NotifyActivitiesImpl.sourceComplete: POST {} for requestId={} source={}", path, requestId, source);
      http.postForEntity(
          baseUrl + path,
          Map.of("requestId", requestId, "source", source),
          Map.class
      );
    } catch (Exception e) {
      // Do not fail the activity/workflow on notify failure; just log
      log.warn("NotifyActivitiesImpl.sourceComplete failed: path={} requestId={} source={} err={}",
          path, requestId, source, e.toString());
    }
  }
}