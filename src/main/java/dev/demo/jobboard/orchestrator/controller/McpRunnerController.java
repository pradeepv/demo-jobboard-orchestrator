package dev.demo.jobboard.orchestrator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/local")
public class McpRunnerController {
    private static final Logger log = LoggerFactory.getLogger(McpRunnerController.class);

    // Registry of active processes keyed by runId
    private final ConcurrentMap<String, Process> processRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastActivity = new ConcurrentHashMap<>();
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    private final ObjectMapper objectMapper;

    public McpRunnerController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // POST /api/local/crawl launches Python and returns runId + sseUrl
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

        // Resolve paths: adjust if your repo layout is different
        Path cwd = Path.of(System.getProperty("user.dir")).resolve("../mcp-jobboard").normalize();
        Path python = cwd.resolve(".venv/bin/python"); // macOS/Linux venv

        List<String> cmd = new ArrayList<>();
        cmd.add(python.toString());
        cmd.add("-u");
        cmd.add("main.py");
        if (StringUtils.hasText(sources)) {
            cmd.add("--sources"); cmd.add(sources);
        }
        if (StringUtils.hasText(keywords)) {
            cmd.add("--keywords"); cmd.add(keywords);
        }
        if (maxPages > 0) {
            cmd.add("--max-pages"); cmd.add(String.valueOf(maxPages));
        }
        if (perSourceLimit > 0) {
            cmd.add("--per-source-limit"); cmd.add(String.valueOf(perSourceLimit));
        }
        if (json) {
            cmd.add("--json");
        }
        if (remoteOnly) {
            cmd.add("--remote-only");
        }
        if (StringUtils.hasText(location)) {
            cmd.add("--location"); cmd.add(location);
        }

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

    // GET /api/local/stream/{runId}: stream stdout line-by-line as SSE
    @GetMapping(path = "/stream/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout for dev

        // Notify client that stream is open
        try {
            Map<String, Object> connected = new LinkedHashMap<>();
            connected.put("ok", true);
            connected.put("runId", runId);
            connected.put("ts", System.currentTimeMillis());
            emitter.send(SseEmitter.event().name("connected").data(connected));
        } catch (IOException e) {
            log.warn("[{}] Failed to send connected event", runId, e);
        }

        streamExecutor.submit(() -> {
            Process process = processRegistry.get(runId);
            if (process == null) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", "No active process for runId " + runId
                    )));
                } catch (IOException ignored) {}
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

                    // Prefer named SSE event from JSON "type"
                    String eventName = null;
                    try {
                        JsonNode node = objectMapper.readTree(trimmed);
                        if (node.has("type") && node.get("type").isTextual()) {
                            eventName = node.get("type").asText();
                        }
                    } catch (Exception parseIgnored) {
                        // not JSON; fall back
                    }

                    try {
                        if (eventName != null && !eventName.isBlank()) {
                            emitter.send(SseEmitter.event().name(eventName).data(trimmed));
                        } else {
                            emitter.send(trimmed); // default "message"
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
                try {
                    exit = process.waitFor();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
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

    // DELETE /api/local/crawl/{runId}: stop the run
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