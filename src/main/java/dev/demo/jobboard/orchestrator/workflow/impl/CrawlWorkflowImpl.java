package dev.demo.jobboard.orchestrator.workflow.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.CrawlRequest;
import dev.demo.jobboard.orchestrator.workflow.model.JobPosting;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class CrawlWorkflowImpl implements CrawlWorkflow {

  private final McpActivities mcp = Workflow.newActivityStub(
      McpActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(15))
          .build());

  private final StreamActivities stream = Workflow.newActivityStub(
      StreamActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .build());

  @Override
  public void start(CrawlRequest request) {
    String reqId = request.getRequestId();
    List<String> roles = request.getRoles();
    String query = (roles == null || roles.isEmpty())
        ? ""
        : roles.stream().collect(Collectors.joining(", ")); // for the stub

    String[] sources = new String[] {"YC", "HN"};
    for (String source : sources) {
      for (int page = 0; page < 2; page++) {
        List<JobPosting> items = mcp.mcpSearch(source, query, page);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"source\":\"").append(source).append("\",\"items\":[");
        for (int i = 0; i < items.size(); i++) {
          JobPosting j = items.get(i);
          sb.append("{\"id\":\"").append(j.getId()).append("\",")
            .append("\"title\":\"").append(j.getTitle()).append("\",")
            .append("\"company\":\"").append(j.getCompany()).append("\"}");
          if (i < items.size() - 1) sb.append(",");
        }
        sb.append("]}");

        stream.emit("crawl", reqId, "job_chunk", sb.toString());
      }
    }
    stream.emit("crawl", reqId, "complete", "{}");
  }
}