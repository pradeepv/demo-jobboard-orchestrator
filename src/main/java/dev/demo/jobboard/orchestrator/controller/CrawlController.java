package dev.demo.jobboard.orchestrator.controller;

import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CrawlController {

    private final WorkflowClient client;

    public CrawlController(WorkflowClient client) {
        this.client = client;
    }

    @PostMapping("/crawl")
    public Map<String, Object> startCrawl(
            @RequestParam String sources,
            @RequestParam String keywords,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "false") boolean remoteOnly,
            @RequestParam(name = "maxPages", defaultValue = "1") int maxPages,
            @RequestParam(name = "perSourceLimit", defaultValue = "50") int perSourceLimit) {

        String workflowId = "crawl-" + UUID.randomUUID();

        List<String> sourcesList = Arrays.asList(sources.split(","));

        CrawlWorkflow.CrawlRequest crawlRequest = new CrawlWorkflow.CrawlRequest(
                sourcesList, keywords, perSourceLimit, 1
        );

        CrawlWorkflow workflow = client.newWorkflowStub(
                CrawlWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(WorkflowController.JOB_BOARD_TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .build()
        );

        WorkflowClient.start(workflow::start, crawlRequest);

        return Map.of(
                "status", "started",
                "requestId", workflowId,
                "sseUrl", "/api/stream/" + workflowId
        );
    }
}
