package dev.demo.jobboard.orchestrator.activity.impl;

import java.time.Instant;
import java.util.stream.Collectors;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.dto.JobPosting;
import dev.demo.jobboard.orchestrator.mcp.McpClient;

public class McpActivitiesImpl implements McpActivities {

  private final McpClient mcp;

  public McpActivitiesImpl(McpClient mcp) {
    this.mcp = mcp;
  }

  @Override
  public void executeSearch(String requestId, String source, String query, int maxPages, int perSourceLimit) {
    // Execute the search which will stream results through the event bus
    mcp.executeSearch(requestId, source, query, maxPages, perSourceLimit);
  }

  @Override
  public PageResult fetchPage(String requestId, String source, String query, int page, int pageSize) {
    // For backward compatibility
    McpClient.McpPage res = mcp.fetchPage(requestId, source, query, page, pageSize);

    java.util.List<PostingSummary> items = res.items == null
        ? java.util.Collections.emptyList()
        : res.items.stream().map(jp -> toSummary(jp, source)).collect(Collectors.toList());

    return new PageResult(page, res.hasMore, items);
  }

  @Override
  public ParsedJob parseJobUrl(String requestId, String url) {
    // Placeholder implementation
    ParsedJob parsed = new ParsedJob();
    parsed.url = url;
    parsed.source = inferSource(url);
    parsed.title = inferTitle(url);
    parsed.company = inferCompany(url);
    parsed.location = "Unknown";
    parsed.salary = null;
    parsed.team = null;
    parsed.description = "Description unavailable (stub).";

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