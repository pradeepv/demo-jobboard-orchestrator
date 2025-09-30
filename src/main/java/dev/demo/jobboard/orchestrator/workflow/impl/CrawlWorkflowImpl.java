package dev.demo.jobboard.orchestrator.workflow.impl;

import java.time.Duration;
import java.util.List;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.McpActivities.PostingSummary;
import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrawlWorkflowImpl implements CrawlWorkflow {

  private static final Logger logger = LoggerFactory.getLogger(CrawlWorkflowImpl.class);

  private final McpActivities mcp = Workflow.newActivityStub(
      McpActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofMinutes(15)) // Longer timeout for streaming
          .build()
  );

  @Override
  public CrawlResult start(CrawlRequest request) {
    if (request == null) {
      throw Workflow.wrap(new IllegalArgumentException("CrawlRequest must not be null"));
    }
    logger.info("CrawlWorkflowImpl.start: request={}", request);

    // Use workflowId as requestId so activities can route events properly
    String requestId = Workflow.getInfo().getWorkflowId();
    
    // Execute searches for each source using the new streaming approach
    for (String source : request.sources) {
      mcp.executeSearch(requestId, source, request.query, request.maxItems, 25);
    }

    // For streaming operations, we return immediately
    // The actual results are delivered via SSE as they arrive
    logger.info("CrawlWorkflow completed for request: {}", requestId);
    return new CrawlResult(null, 1, false); // Placeholder - actual results are streamed
  }

  private final dev.demo.jobboard.orchestrator.activity.NotifyActivities notify =
    Workflow.newActivityStub(
        dev.demo.jobboard.orchestrator.activity.NotifyActivities.class,
        io.temporal.activity.ActivityOptions.newBuilder()
            .setStartToCloseTimeout(java.time.Duration.ofSeconds(10))
            .build()
    );
}