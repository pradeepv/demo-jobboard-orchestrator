package dev.demo.jobboard.orchestrator.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.demo.jobboard.orchestrator.config.TemporalConfig;
import dev.demo.jobboard.orchestrator.workflow.AnalysisWorkflow;
import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import dev.demo.jobboard.orchestrator.workflow.model.CrawlRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@RestController
@RequestMapping("/api")
public class WorkflowController {

  private final WorkflowClient client;

  public WorkflowController(WorkflowClient client) {
    this.client = client;
  }

  // Accept roles as repeated query params (?roles=backend&roles=java) or comma-separated if using a single param
  @PostMapping("/crawl")
  public ResponseEntity<Map<String, String>> startCrawl(@RequestParam List<String> roles) {
    String requestId = UUID.randomUUID().toString();
    CrawlWorkflow wf = client.newWorkflowStub(
        CrawlWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue(TemporalConfig.TASK_QUEUE)
            .setWorkflowId("crawl-" + requestId)
            .build());
    WorkflowClient.start(wf::start, new CrawlRequest(requestId, roles));
    return ResponseEntity.ok(Map.of(
        "requestId", requestId,
        "sseUrl", "/api/stream/crawl/" + requestId
    ));
  }

  @PostMapping("/analyze")
  public ResponseEntity<Map<String, String>> startAnalysis(
      @RequestParam String requestId,
      @RequestParam String resumeText,
      @RequestParam List<String> jobIds
  ) {
    AnalysisWorkflow wf = client.newWorkflowStub(
        AnalysisWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue(TemporalConfig.TASK_QUEUE)
            .setWorkflowId("analyze-" + requestId)
            .build());
    WorkflowClient.start(wf::start, new AnalysisRequest(requestId, resumeText, jobIds));
    return ResponseEntity.ok(Map.of(
        "requestId", requestId,
        "sseUrl", "/api/stream/analyze/" + requestId
    ));
  }
}