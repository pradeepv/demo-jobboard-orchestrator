package dev.demo.jobboard.orchestrator.controller;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import dev.demo.jobboard.orchestrator.util.Channels;

@RestController
@RequestMapping("/api")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);
    private final SseEventBus bus;

    public WorkflowController(SseEventBus bus) {
        this.bus = bus;
    }

    @PostMapping(path = "/crawl", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> startCrawl(@RequestParam("roles") String roles) {
        String requestId = "crawl-" + UUID.randomUUID();
        String channel = Channels.forRequest(requestId);
        log.debug("startCrawl: requestId={} channel={} roles={}", requestId, channel, roles);

        // TODO: Start your workflow here. Ensure it receives requestId and uses Channels.forRequest(requestId).
        // e.g., workflowClient.newWorkflowStub(...).start(new StartParams(requestId, roles));

        // Optionally emit a start marker
        bus.publish(channel, "crawl", Map.of("kind", "crawlStart", "payload", Map.of("roles", roles)));

        return Map.of(
            "requestId", requestId,
            "sseUrl", "/api/stream/" + requestId
        );
    }

    @GetMapping(path = "/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAll(@PathVariable String requestId) {
        String channel = Channels.forRequest(requestId);
        log.debug("streamAll: requestId={} channel={}", requestId, channel);
        return bus.addSubscriber(channel);
    }

    // Debug endpoints (require FULL requestId)
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
}