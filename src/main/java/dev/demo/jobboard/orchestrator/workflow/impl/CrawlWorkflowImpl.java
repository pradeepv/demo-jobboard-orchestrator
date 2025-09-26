package dev.demo.jobboard.orchestrator.workflow.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.McpActivities.PageResult;
import dev.demo.jobboard.orchestrator.activity.McpActivities.PostingSummary;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class CrawlWorkflowImpl implements CrawlWorkflow {

  private final McpActivities mcp = Workflow.newActivityStub(
      McpActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          .build()
  );

  private final StreamActivities stream = Workflow.newActivityStub(
      StreamActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(15))
          .build()
  );

  @Override
  public CrawlResult start(CrawlRequest request) {
    if (request == null) {
      throw Workflow.wrap(new IllegalArgumentException("CrawlRequest must not be null"));
    }

    String source = safeTrim(request.source);
    String query = safeTrim(request.query);
    int maxItems = request.maxItems > 0 ? request.maxItems : 200;
    int page = request.startPage > 0 ? request.startPage : 1;
    int pageSize = 25; // tune as needed

    List<PostingSummary> all = new ArrayList<>(Math.min(maxItems, 256));
    boolean hasMore = true;
    int lastPageFetched = page - 1;

    // Use workflowId as requestId so activities can route SSE to req:<workflowId>
    String requestId = Workflow.getInfo().getWorkflowId();
    String channel = channelForRequest(requestId); // mirrors controller's Channels.forRequest

    while (hasMore && all.size() < maxItems) {
      PageResult batch = mcp.fetchPage(requestId, source, query, page, pageSize);

      List<PostingSummary> pageItems = List.of();
      if (batch != null && batch.items != null && !batch.items.isEmpty()) {
        // Apply cap to this page
        int remaining = maxItems - all.size();
        if (remaining <= 0) {
          pageItems = List.of();
        } else if (batch.items.size() > remaining) {
          pageItems = batch.items.subList(0, remaining);
        } else {
          pageItems = batch.items;
        }
        all.addAll(pageItems);
      }

      lastPageFetched = batch == null ? page : batch.page;
      boolean batchHasMore = batch != null && batch.hasMore;
      hasMore = batchHasMore && all.size() < maxItems;

      // Stream this page (even if empty, to surface progress and lastPageFetched)
      Map<String, Object> payload = new HashMap<>();
      payload.put("kind", "page");
      payload.put("source", source);
      payload.put("query", query);
      payload.put("page", lastPageFetched);
      payload.put("pageSize", pageSize);
      payload.put("hasMore", hasMore);
      payload.put("items", pageItems);

      // type="crawl" (consistent channel type used by your controller/debug endpoints)
      stream.emit(channel, requestId, "crawl", toJson(payload));

      // next page
      page = (batch == null ? page : batch.page) + 1;
    }

    boolean truncated = all.size() >= maxItems && hasMore;

    // Emit completion event
    Map<String, Object> donePayload = new HashMap<>();
    donePayload.put("kind", "crawlComplete");
    donePayload.put("source", source);
    donePayload.put("lastPageFetched", lastPageFetched);
    donePayload.put("truncated", truncated);
    donePayload.put("totalItems", all.size());
    stream.emit(channel, requestId, "crawl", toJson(donePayload));

    notify.sourceComplete(requestId, source);
  
    return new CrawlResult(all, lastPageFetched, truncated);
  }

  private static String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }

  // Keep this in sync with your controller/Channels.forRequest convention.
  // If your StreamActivities implementation already expects Channels.forRequest(requestId),
  // it can derive the same string internally. Here we mirror "req:" + requestId.
  private static String channelForRequest(String requestId) {
    return "req:" + requestId;
  }

  private final dev.demo.jobboard.orchestrator.activity.NotifyActivities notify =
    Workflow.newActivityStub(
        dev.demo.jobboard.orchestrator.activity.NotifyActivities.class,
        io.temporal.activity.ActivityOptions.newBuilder()
            .setStartToCloseTimeout(java.time.Duration.ofSeconds(10))
            .build()
    );

  private static String toJson(Object obj) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
    } catch (Exception e) {
      // minimal fallback to avoid failing the workflow on serialization errors
      return "{"error":"json-serialization-failed"}";
    }
  }
}