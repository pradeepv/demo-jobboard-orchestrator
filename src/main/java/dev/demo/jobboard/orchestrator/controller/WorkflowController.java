package dev.demo.jobboard.orchestrator.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.demo.jobboard.orchestrator.config.TemporalConfig;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import dev.demo.jobboard.orchestrator.util.Channels;
import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@RestController
@RequestMapping("/api")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);
    private final SseEventBus bus;
    private final WorkflowClient workflowClient;

    private final ConcurrentHashMap<String, AtomicInteger> expectedByReq = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> completedByReq = new ConcurrentHashMap<>();

    public WorkflowController(SseEventBus bus, WorkflowClient workflowClient) {
        this.bus = bus;
        this.workflowClient = workflowClient;
    }

    @PostMapping(path = "/crawl", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> startCrawl(@RequestParam("roles") String roles) {
        String requestId = "crawl-" + UUID.randomUUID();
        String channel = Channels.forRequest(requestId);
        log.debug("startCrawl: requestId={} channel={} roles={}", requestId, channel, roles);

        // Emit a start marker for the UI
        bus.publish(channel, "crawl", Map.of(
            "kind", "crawlStart",
            "payload", Map.of(
                "roles", roles,
                "requestId", requestId,
                "ts", Instant.now().toString()
            )
        ));

        // Build a simple free-text query from roles CSV
        String query = Arrays.stream(roles.split("\\s*,\\s*"))
            .filter(s -> !s.isBlank())
            .reduce((a, b) -> a + " " + b)
            .orElse(roles);

        // Default sources to crawl; each runs in its own workflow
        List<String> sources = List.of("ycombinator", "hackernews_jobs");

        expectedByReq.put(requestId, new AtomicInteger(sources.size()));
        completedByReq.put(requestId, new AtomicInteger(0));

        // Start one workflow per source asynchronously
        for (String source : sources) {
            WorkflowOptions opts = WorkflowOptions.newBuilder()
                .setTaskQueue(TemporalConfig.JOB_BOARD_TASK_QUEUE)
                .setWorkflowId("crawl-" + requestId + "-" + source)
                .build();

            CrawlWorkflow stub = workflowClient.newWorkflowStub(CrawlWorkflow.class, opts);
            CrawlWorkflow.CrawlRequest wfReq = new CrawlWorkflow.CrawlRequest(
                source, query, 200, 1
            );

            WorkflowClient.start(stub::start, wfReq);
        }

        return Map.of(
            "requestId", requestId,
            "sseUrl", "/api/stream/" + requestId
        );
    }

    @GetMapping(path = "/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAll(@PathVariable String requestId) {
        String channel = Channels.forRequest(requestId);
        log.debug("streamAll: requestId={} channel={}", requestId, channel);

        // Let the bus create/own the emitter for this channel
        SseEmitter emitter = bus.addSubscriber(channel);

        // Immediately publish a "connected" event to flush headers/first bytes
        try {
            bus.publish(channel, "connected", Map.of("ok", true, "id", requestId));
        } catch (Exception e) {
            log.warn("Failed to publish connected for {}: {}", requestId, e.toString());
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
            return emitter;
        }

        // Heartbeat to keep the connection alive (Chrome/Proxies)
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat-" + requestId);
            t.setDaemon(true);
            return t;
        });
        ScheduledFuture<?> pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                bus.publish(channel, "ping", Map.of("ts", Instant.now().toEpochMilli()));
            } catch (Exception e) {
                // If publish fails (client likely gone), complete with error to trigger cleanup
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        }, 15, 15, TimeUnit.SECONDS);

        // Cleanup hooks
        emitter.onCompletion(() -> {
            try { pingTask.cancel(true); } catch (Exception ignored) {}
            try { scheduler.shutdownNow(); } catch (Exception ignored) {}
            log.debug("streamAll completed: {}", requestId);
        });
        emitter.onTimeout(() -> {
            try { pingTask.cancel(true); } catch (Exception ignored) {}
            try { scheduler.shutdownNow(); } catch (Exception ignored) {}
            try { emitter.complete(); } catch (Exception ignored) {}
            log.debug("streamAll timeout: {}", requestId);
        });
        emitter.onError((ex) -> {
            try { pingTask.cancel(true); } catch (Exception ignored) {}
            try { scheduler.shutdownNow(); } catch (Exception ignored) {}
            log.warn("streamAll error: {} -> {}", requestId, ex.toString());
        });

        return emitter;
    }

    // Debug endpoints
    @PostMapping(path = "/debug/crawl/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> debugSend(@RequestParam("id") String requestId,
                                         @RequestParam("msg") String msg) {
        String channel = Channels.forRequest(requestId);
        log.debug("debugSend: requestId={} channel={} msg={}", requestId, channel, msg);
        bus.publish(channel, "crawl", Map.of("kind", "page", "payload", Map.of("title", msg)));
        return Map.of("sent", true, "requestId", requestId);
    }

    @PostMapping(path = "/debug/crawl/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> debugComplete(@RequestParam("id") String requestId) {
        String channel = Channels.forRequest(requestId);
        log.debug("debugComplete: requestId={} channel={}", requestId, channel);
        bus.publish(channel, "crawl", Map.of("kind", "crawlComplete"));
        bus.complete(channel, requestId);
        return Map.of("requestId", requestId, "completed", true);
    }

    @PostMapping(path = "/crawl/complete-source", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> onSourceComplete(@RequestBody Map<String, String> body) {
        String requestId = body.getOrDefault("requestId", "");
        String source = body.getOrDefault("source", "");
        String channel = Channels.forRequest(requestId);

        int done = completedByReq.computeIfAbsent(requestId, k -> new AtomicInteger(0)).incrementAndGet();
        int expected = expectedByReq.getOrDefault(requestId, new AtomicInteger(0)).get();

        log.debug("onSourceComplete: requestId={} source={} done={}/{}", requestId, source, done, expected);

        bus.publish(channel, "crawl", Map.of(
            "kind", "sourceComplete",
            "source", source,
            "completed", done,
            "expected", expected
        ));

        if (done >= expected) {
            bus.publish(channel, "crawl", Map.of("kind", "allComplete", "totalSources", expected));
            bus.complete(channel, requestId);
            expectedByReq.remove(requestId);
            completedByReq.remove(requestId);
        }

        return Map.of("ok", true, "requestId", requestId, "source", source, "done", done, "expected", expected);
    }
}