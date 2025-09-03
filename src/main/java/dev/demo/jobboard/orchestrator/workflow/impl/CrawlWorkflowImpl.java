package dev.demo.jobboard.orchestrator.workflow.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.McpActivities.PageResult;
import dev.demo.jobboard.orchestrator.activity.McpActivities.PostingSummary;
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

    while (hasMore && all.size() < maxItems) {
      PageResult batch = mcp.fetchPage(requestId, source, query, page, pageSize);

      if (batch != null && batch.items != null && !batch.items.isEmpty()) {
        for (PostingSummary it : batch.items) {
          if (all.size() >= maxItems) break;
          all.add(it);
        }
      }

      lastPageFetched = batch == null ? page : batch.page;
      hasMore = batch != null && batch.hasMore && all.size() < maxItems;
      page = (batch == null ? page : batch.page) + 1;
    }

    boolean truncated = all.size() >= maxItems && hasMore;
    return new CrawlResult(all, lastPageFetched, truncated);
  }

  private static String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }
}