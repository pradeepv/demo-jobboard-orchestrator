package dev.demo.jobboard.orchestrator.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.demo.jobboard.orchestrator.config.McpConfig;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/local")
public class McpRunnerController {
    private static final Logger log = LoggerFactory.getLogger(McpRunnerController.class);

    private final ConcurrentMap<String, Process> processRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastActivity = new ConcurrentHashMap<>();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

    private final ObjectMapper objectMapper;
    private final McpConfig mcpConfig;

    public McpRunnerController(ObjectMapper objectMapper, McpConfig mcpConfig) {
        this.objectMapper = objectMapper;
        this.mcpConfig = mcpConfig;
    }

    @PostMapping(path = "/crawl", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> start(
        @RequestParam String sources,
        @RequestParam String keywords,
        @RequestParam(defaultValue = "") String location,
        @RequestParam(defaultValue = "false") boolean remoteOnly,
        @RequestParam(name = "maxPages", defaultValue = "1") int maxPages,
        @RequestParam(name = "perSourceLimit", defaultValue = "50") int perSourceLimit,
        @RequestParam(defaultValue = "true") boolean json
    ) throws IOException {
        String runId = "run-" + UUID.randomUUID();

        log.info("[{}] Starting local crawl: sources={}, keywords='{}', location='{}', remoteOnly={}, maxPages={}, perSourceLimit={}, json={}",
            runId, sources, keywords, location, remoteOnly, maxPages, perSourceLimit, json);

        Path cwd = Path.of(mcpConfig.getResolvedWorkingDirectory());
        Path python = Path.of(mcpConfig.getFullCommand());

        List<String> cmd = new ArrayList<>();
        cmd.add(python.toString());
        cmd.add("-u");
        cmd.add(mcpConfig.getMainScript());
        if (StringUtils.hasText(sources)) { cmd.add("--sources"); cmd.add(sources); }
        if (StringUtils.hasText(keywords)) { cmd.add("--keywords"); cmd.add(keywords); }
        if (maxPages > 0) { cmd.add("--max-pages"); cmd.add(String.valueOf(maxPages)); }
        if (perSourceLimit > 0) { cmd.add("--per-source-limit"); cmd.add(String.valueOf(perSourceLimit)); }
        if (json) { cmd.add("--json"); }
        if (remoteOnly) { cmd.add("--remote-only"); }
        if (StringUtils.hasText(location)) { cmd.add("--location"); cmd.add(location); }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        processRegistry.put(runId, process);
        lastActivity.put(runId, System.currentTimeMillis());

        log.info("[{}] Exec: {} (cwd={})", runId, String.join(" ", cmd), cwd);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("runId", runId);
        resp.put("sseUrl", "/api/local/stream/" + runId);
        return resp;
    }

    @GetMapping(path = "/stream/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId, HttpServletResponse response) {
        // Critical SSE headers
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // Optional for nginx: response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(0L); // no timeout for dev

        // Initial handshake + heartbeat
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of(
                "ok", true, "runId", runId, "ts", System.currentTimeMillis()
            )));
        } catch (IOException e) {
            log.warn("[{}] Failed to send connected event", runId, e);
        }

        ScheduledFuture<?> hb = heartbeats.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping " + System.currentTimeMillis()));
            } catch (Exception ex) {
                // client likely closed; let lifecycle handlers remove
            }
        }, 15, 15, TimeUnit.SECONDS);

        emitter.onCompletion(() -> hb.cancel(false));
        emitter.onTimeout(() -> hb.cancel(false));
        emitter.onError((ex) -> hb.cancel(false));

        streamExecutor.submit(() -> {
            Process process = processRegistry.get(runId);
            if (process == null) {
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", "No active process"))); }
                catch (IOException ignored) {}
                emitter.complete();
                return;
            }

            // Drain stderr concurrently
            streamExecutor.submit(() -> {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String el;
                    while ((el = err.readLine()) != null) {
                        log.warn("[{}][stderr] {}", runId, el);
                    }
                } catch (IOException ioe) {
                    log.debug("[{}] stderr closed", runId);
                }
            });

            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = out.readLine()) != null) {
                    lastActivity.put(runId, System.currentTimeMillis());
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    String eventName = null;
                    try {
                        JsonNode node = objectMapper.readTree(trimmed);
                        if (node.has("type") && node.get("type").isTextual()) {
                            eventName = node.get("type").asText();
                        }
                    } catch (Exception parseIgnored) {}

                    try {
                        if (eventName != null && !eventName.isBlank()) {
                            emitter.send(SseEmitter.event().name(eventName).data(trimmed));
                        } else {
                            emitter.send(trimmed);
                        }
                    } catch (IOException sendErr) {
                        log.warn("[{}] SSE send failed; closing stream", runId, sendErr);
                        break;
                    }
                }
            } catch (IOException io) {
                log.warn("[{}] stdout read error/closed", runId, io);
            } finally {
                int exit = -1;
                try { exit = process.waitFor(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                log.info("[{}] process exited with code {}", runId, exit);

                try {
                    emitter.send(SseEmitter.event().name("complete").data(Map.of(
                        "runId", runId,
                        "exitCode", exit
                    )));
                } catch (IOException ignored) {}
                emitter.complete();

                processRegistry.remove(runId);
                lastActivity.remove(runId);
            }
        });

        return emitter;
    }

    @DeleteMapping(path = "/crawl/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stop(@PathVariable String runId) {
        Process p = processRegistry.remove(runId);
        lastActivity.remove(runId);
        boolean stopped = false;
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopped = true;
            log.info("[{}] process terminated", runId);
        }
        return Map.of("ok", true, "stopped", stopped, "runId", runId);
    }
}