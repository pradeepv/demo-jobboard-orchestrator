package dev.demo.jobboard.orchestrator.activity.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.dto.JobPosting;
import dev.demo.jobboard.orchestrator.mcp.McpClient;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;

public class McpActivitiesImpl implements McpActivities {

  private final McpClient mcp;
  private final SseEventBus bus;

  public McpActivitiesImpl(McpClient mcp, SseEventBus bus) {
    this.mcp = mcp;
    this.bus = bus;
  }

  @Override
  public PageResult fetchPage(String requestId, String source, String query, int page, int pageSize) {
    String channel = "req:" + requestId;

    // Emit start-of-page event
    bus.publish(channel, "crawl", Map.of(
        "stage", "startPage",
        "page", page,
        "ts", Instant.now().toString()
    ));

    // McpClient.search signature: (query, page, pageSize)
    McpClient.McpPage res = mcp.search(query, page, pageSize);

    // SSE payload for this page
    Map<String, Object> payload = new HashMap<>();
    payload.put("stage", "page");
    payload.put("page", page);
    payload.put("count", res.items == null ? 0 : res.items.size());
    payload.put("hasMore", res.hasMore);
    payload.put("ts", Instant.now().toString());
    bus.publish(channel, "crawl", payload);

    // Map dto.JobPosting -> McpActivities.PostingSummary (8-field constructor)
    java.util.List<PostingSummary> items = res.items == null
        ? java.util.Collections.emptyList()
        : res.items.stream().map(jp -> toSummary(jp, source)).collect(Collectors.toList());

    return new PageResult(page, res.hasMore, items);
  }

  private static PostingSummary toSummary(JobPosting jp, String source) {
    // Map each field; use defaults if nulls are possible in JobPosting
    String id = jp.getId();
    String title = jp.getTitle();
    String company = jp.getCompany();
    String location = jp.getLocation();
    String url = jp.getUrl();
    String src = source; // carry through the source provided to activity
    Instant postedAt = jp.getPostedAt(); // ensure JobPosting has this; otherwise use Instant.now() or null
    String snippet = jp.getSnippet();    // ensure JobPosting has this; otherwise derive or set null

    return new PostingSummary(id, title, company, location, url, src, postedAt, snippet);
  }
}