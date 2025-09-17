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

    bus.publish(channel, "crawl", Map.of(
        "stage", "startPage",
        "page", page,
        "ts", Instant.now().toString()
    ));

    McpClient.McpPage res = mcp.search(query, page, pageSize);

    Map<String, Object> payload = new HashMap<>();
    payload.put("stage", "page");
    payload.put("page", page);
    payload.put("count", res.items == null ? 0 : res.items.size());
    payload.put("hasMore", res.hasMore);
    payload.put("ts", Instant.now().toString());
    bus.publish(channel, "crawl", payload);

    java.util.List<PostingSummary> items = res.items == null
        ? java.util.Collections.emptyList()
        : res.items.stream().map(jp -> toSummary(jp, source)).collect(Collectors.toList());

    return new PageResult(page, res.hasMore, items);
  }

  @Override
  public ParsedJob parseJobUrl(String requestId, String url) {
    String channel = "req:" + requestId;

    // Emit start parse event
    bus.publish(channel, "crawl", Map.of(
        "stage", "parseStart",
        "url", url,
        "ts", Instant.now().toString()
    ));

    // Since McpClient currently doesn’t expose a parse-by-URL method,
    // provide a minimal stub that extracts best-effort metadata from the URL.
    // This is safe, testable, and can be upgraded once McpClient supports fetch-by-URL.
    ParsedJob parsed = new ParsedJob();
    parsed.url = url;
    parsed.source = inferSource(url);
    parsed.title = inferTitle(url);
    parsed.company = inferCompany(url);
    parsed.location = "Unknown";
    parsed.salary = null;
    parsed.team = null;
    parsed.description = "Description unavailable (stub).";

    // Emit parsed event
    bus.publish(channel, "crawl", Map.of(
        "stage", "parsed",
        "url", url,
        "title", parsed.title,
        "company", parsed.company,
        "source", parsed.source,
        "ts", Instant.now().toString()
    ));

    return parsed;
  }

  private static PostingSummary toSummary(JobPosting jp, String source) {
    String id = jp.getId();
    String title = jp.getTitle();
    String company = jp.getCompany();
    String location = jp.getLocation();
    String url = jp.getUrl();
    String src = source;
    Instant postedAt = jp.getPostedAt();
    String snippet = jp.getSnippet();
    return new PostingSummary(id, title, company, location, url, src, postedAt, snippet);
  }

  // Simple heuristics while we lack a real parse-by-URL from McpClient
  private static String inferSource(String url) {
    if (url == null) return null;
    try {
      String host = new java.net.URI(url).getHost();
      return host == null ? null : host;
    } catch (Exception e) {
      return null;
    }
  }

  private static String inferCompany(String url) {
    // Example: https://jobs.example.com/uuid => "example"
    String src = inferSource(url);
    if (src == null) return null;
    String[] parts = src.split("\\.");
    if (parts.length >= 2) return parts[parts.length - 2];
    return src;
  }

  private static String inferTitle(String url) {
    // Use last path segment as a placeholder title if present
    try {
      String path = new java.net.URI(url).getPath();
      if (path == null || path.isBlank()) return "Job Posting";
      String[] segs = path.split("/");
      String last = segs.length == 0 ? "" : segs[segs.length - 1];
      if (last.isBlank()) return "Job Posting";
      return "Job: " + last;
    } catch (Exception e) {
      return "Job Posting";
    }
  }
}