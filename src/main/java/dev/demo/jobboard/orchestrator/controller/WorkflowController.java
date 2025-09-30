package dev.demo.jobboard.orchestrator.controller;

import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import dev.demo.jobboard.orchestrator.util.Channels;
import dev.demo.jobboard.orchestrator.workflow.AnalysisWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class WorkflowController {

  private static final Logger log = LoggerFactory.getLogger(
    WorkflowController.class
  );

  private final WorkflowClient client;
  private final SseEventBus bus;

  public static final String JOB_BOARD_TASK_QUEUE = "jobboard-tq";

  public WorkflowController(WorkflowClient client, SseEventBus bus) {
    this.client = client;
    this.bus = bus;
  }

  @PostMapping(
    path = "/analysis",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Map<String, Object> startAnalysis(
    @RequestBody(required = false) Map<String, Object> body
  ) {
    if (body == null) {
      log.warn("startAnalysis: received null body");
      throw new IllegalArgumentException("Request body is required");
    }
    log.info("startAnalysis: incoming body={}", body);

    @SuppressWarnings("unchecked")
    List<String> jobIdsFromBody = (List<String>) body.get("jobIds");
    String jobUrl = (String) body.get("jobUrl");

    boolean hasJobIds = jobIdsFromBody != null && !jobIdsFromBody.isEmpty();
    boolean hasJobUrl = jobUrl != null && !jobUrl.isBlank();

    log.info(
      "startAnalysis: hasJobIds={} size={}, hasJobUrl={} url='{}'",
      hasJobIds,
      hasJobIds ? jobIdsFromBody.size() : 0,
      hasJobUrl,
      hasJobUrl ? jobUrl : "(blank)"
    );

    Assert.isTrue(
      hasJobIds || hasJobUrl,
      "Provide either jobIds (list) or jobUrl (string)"
    );

    List<String> jobIds = hasJobIds ? jobIdsFromBody : List.of(jobUrl);

    String requestId = "analysis-" + UUID.randomUUID();
    String channel = Channels.forRequest(requestId);

    AnalysisWorkflow workflow = client.newWorkflowStub(
      AnalysisWorkflow.class,
      WorkflowOptions.newBuilder()
        .setTaskQueue(JOB_BOARD_TASK_QUEUE)
        .setWorkflowId(requestId)
        .build()
    );

    try {
      bus.publish(
        channel,
        "analysis",
        Map.of(
          "kind",
          "analysisStart",
          "stage",
          "starting",
          "message",
          hasJobUrl
            ? "Starting analysis for provided URL"
            : "Starting analysis",
          "totalJobs",
          jobIds.size(),
          "progress",
          0
        )
      );
    } catch (Exception e) {
      log.warn(
        "startAnalysis: failed to publish initial SSE event for {}",
        requestId,
        e
      );
    }

    AnalysisRequest req = new AnalysisRequest();
    req.setRequestId(requestId);
    req.setJobIds(jobIds);

    try {
      io.temporal.workflow.Functions.Proc startProc = () -> workflow.start(req);
      WorkflowClient.start(startProc);
    } catch (Exception e) {
      log.error("startAnalysis: failed to start workflow for {}", requestId, e);
      throw e;
    }

    String sseUrl = "/api/stream/" + requestId;
    Map<String, Object> resp = Map.of(
      "status",
      "started",
      "requestId",
      requestId,
      "sseUrl",
      sseUrl
    );
    log.info("startAnalysis: responding {}", resp);
    return resp;
  }

  /*
    @GetMapping(path = "/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAll(@PathVariable("id") String id, HttpServletResponse response) {
        // Critical for proxies: prevent buffering/transform
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // Optional (nginx): response.setHeader("X-Accel-Buffering", "no");

        String channel = Channels.forRequest(id);
        return bus.subscribe(channel, response);
    }
*/

  @PostMapping(
    path = "/stream/ingest",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Map<String, Object> ingestEvent(
    @RequestBody Map<String, Object> body
  ) {
    String requestId = (String) body.getOrDefault("requestId", "");
    String eventName = (String) body.getOrDefault("event", "analysis");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) body.getOrDefault(
      "payload",
      Map.of()
    );

    String channel = Channels.forRequest(requestId);
    bus.publish(channel, eventName, payload);
    return Map.of("ok", true, "requestId", requestId, "event", eventName);
  }

  @PostMapping(
    path = "/analysis/complete-source",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Map<String, Object> onAnalysisComplete(
    @RequestBody Map<String, String> body
  ) {
    String requestId = body.getOrDefault("requestId", "");
    String source = body.getOrDefault("source", "analysis");
    String channel = Channels.forRequest(requestId);

    log.debug("onAnalysisComplete: requestId={} source={}", requestId, source);

    bus.publish(
      channel,
      "analysis",
      Map.of("kind", "analysisAllComplete", "source", source)
    );
    bus.complete(channel, requestId);

    return Map.of(
      "ok",
      true,
      "requestId",
      requestId,
      "source",
      source,
      "completed",
      true
    );
  }

  @PostMapping(path = "/debug/analysis/send")
  public Map<String, Object> debugSend(
    @RequestParam("id") String requestId,
    @RequestParam("msg") String msg
  ) {
    String channel = Channels.forRequest(requestId);
    bus.publish(channel, "analysis", Map.of("kind", "debug", "msg", msg));
    return Map.of("ok", true);
  }
}
