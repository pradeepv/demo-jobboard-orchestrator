package dev.demo.jobboard.orchestrator.controller;

import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api")
public class CrawlController {

  private final WorkflowClient client;
  private final SseEventBus eventBus;
  private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

  private final ObjectMapper objectMapper;

  private static final Logger logger = LoggerFactory.getLogger(
    CrawlController.class
  );

  public CrawlController(WorkflowClient client, ObjectMapper objectMapper, SseEventBus eventBus) {
    this.objectMapper = objectMapper;
    this.client = client;
    this.eventBus = eventBus;
  }

  @PostMapping("/crawl")
  public Map<String, Object> startCrawl(
    @RequestParam String sources,
    @RequestParam String keywords,
    @RequestParam(defaultValue = "") String location,
    @RequestParam(defaultValue = "false") boolean remoteOnly,
    @RequestParam(name = "maxPages", defaultValue = "1") int maxPages,
    @RequestParam(
      name = "perSourceLimit",
      defaultValue = "50"
    ) int perSourceLimit
  ) {
    String workflowId = "crawl-" + UUID.randomUUID();

    List<String> sourcesList = Arrays.asList(sources.split(","));
    
    CrawlWorkflow.CrawlRequest crawlRequest = new CrawlWorkflow.CrawlRequest(
      sourcesList,
      keywords,
      perSourceLimit,
      1
    );

    CrawlWorkflow workflow = client.newWorkflowStub(
      CrawlWorkflow.class,
      WorkflowOptions.newBuilder()
        .setTaskQueue(WorkflowController.JOB_BOARD_TASK_QUEUE)
        .setWorkflowId(workflowId)
        .build()
    );

    WorkflowClient.start(workflow::start, crawlRequest);

    logger.info(
      "CrawlController.startCrawl: started workflowId={}",
      workflowId
    );

    return Map.of(
      "status",
      "started",
      "requestId",
      workflowId,
      "runId",
      workflowId,
      "sseUrl",
      "/api/stream/" + workflowId
    );
  }

  @GetMapping(path = "/stream/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String runId, HttpServletResponse response) {
      // Critical SSE headers
      response.setHeader("Cache-Control", "no-cache, no-transform");
      response.setHeader("Connection", "keep-alive");
      response.setHeader("X-Accel-Buffering", "no"); // Disable proxy buffering for SSE

      SseEmitter emitter = new SseEmitter(0L); // no timeout for dev

      // Initial handshake + heartbeat
      try {
          emitter.send(SseEmitter.event().name("connected").data(Map.of(
              "ok", true, "runId", runId, "ts", System.currentTimeMillis()
          )));
      } catch (IOException e) {
          logger.warn("[{}] Failed to send connected event", runId, e);
      }

      ScheduledFuture<?> hb = heartbeats.scheduleAtFixedRate(() -> {
          try {
              emitter.send(SseEmitter.event().comment("ping " + System.currentTimeMillis()));
          } catch (Exception ex) {
              // client likely closed; let lifecycle handlers remove
          }
      }, 15, 15, TimeUnit.SECONDS);

      // Setup lifecycle handlers
      emitter.onCompletion(() -> {
          hb.cancel(false);
          eventBus.unsubscribe("req:" + runId, emitter);
      });
      emitter.onTimeout(() -> hb.cancel(false));
      emitter.onError((ex) -> {
          hb.cancel(false);
          eventBus.unsubscribe("req:" + runId, emitter);
      });

      // Attach the emitter to the channel to receive events from MCP
      eventBus.attachEmitter("req:" + runId, emitter);

      return emitter;
  }

}