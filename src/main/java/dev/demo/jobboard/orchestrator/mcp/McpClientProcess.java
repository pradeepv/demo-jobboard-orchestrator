package dev.demo.jobboard.orchestrator.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.demo.jobboard.orchestrator.config.McpConfig;
import dev.demo.jobboard.orchestrator.dto.JobDetails;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;

public class McpClientProcess implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClientProcess.class);
    private final McpConfig cfg;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SseEventBus sseEventBus;  // Direct access to existing event bus
    private final ConcurrentMap<String, Process> processRegistry = new ConcurrentHashMap<>();
    private final ExecutorService processExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mcp-client-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    public McpClientProcess(McpConfig cfg, SseEventBus sseEventBus) {
        this.cfg = cfg;
        this.sseEventBus = sseEventBus;
    }

    @Override
    public void executeSearch(String requestId, String source, String query, int maxPages, int perSourceLimit) {
        // Prevent duplicate executions
        if (processRegistry.containsKey(requestId)) {
            log.warn("[{}] Search already in progress for this request ID", requestId);
            return;
        }
        
        // Submit search execution
        processExecutor.submit(() -> performSearch(requestId, source, query, maxPages, perSourceLimit));
    }

    private void performSearch(String requestId, String source, String query, int maxPages, int perSourceLimit) {
        Path cwd = Path.of(cfg.getResolvedWorkingDirectory());
        String pythonPath = cfg.getFullCommand();
        
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPath);
        cmd.add("-u"); // Unbuffered output for real-time streaming
        cmd.add(cfg.getMainScript());
        if (StringUtils.hasText(source)) { cmd.add("--sources"); cmd.add(source); }
        if (StringUtils.hasText(query)) { cmd.add("--keywords"); cmd.add(query); }
        cmd.add("--max-pages"); cmd.add(String.valueOf(maxPages));
        cmd.add("--per-source-limit"); cmd.add(String.valueOf(perSourceLimit));
        cmd.add("--json");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false); // Keep stderr separate
        
        Process process = null;
        try {
            log.info("[{}] Starting MCP process: {}", requestId, String.join(" ", cmd));
            process = pb.start();
            processRegistry.put(requestId, process);
            
            // Emit start event
            String channel = "req:" + requestId; // Consistent with Channels.forRequest
            sseEventBus.publish(channel, "crawlStart", Map.of(
                "source", source,
                "query", query,
                "maxPages", maxPages,
                "perSourceLimit", perSourceLimit,
                "timestamp", Instant.now().toString()
            ));

            // Handle stdout (JSON events from MCP)
            handleMcpOutput(requestId, process.getInputStream(), channel);
            
            // Handle stderr (logs from MCP)
            handleMcpError(requestId, process.getErrorStream());

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[{}] MCP process timed out, forcing termination", requestId);
                process.destroyForcibly();
                sseEventBus.publish(channel, "error", Map.of(
                    "message", "Search operation timed out",
                    "timestamp", Instant.now().toString()
                ));
                return;
            }

            int exitCode = process.exitValue();
            log.info("[{}] MCP process completed with exit code: {}", requestId, exitCode);
            
            // Emit completion event
            sseEventBus.publish(channel, "complete", Map.of(
                "exitCode", exitCode,
                "timestamp", Instant.now().toString()
            ));

        } catch (Exception e) {
            log.error("[{}] MCP process failed: {}", requestId, e.getMessage(), e);
            
            String channel = "req:" + requestId;
            sseEventBus.publish(channel, "error", Map.of(
                "message", "MCP process failed: " + e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        } finally {
            processRegistry.remove(requestId);
            
            if (process != null && process.isAlive()) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignore) {}
            }
        }
    }

    private void handleMcpOutput(String requestId, java.io.InputStream inputStream, String channel) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonNode event = mapper.readTree(line);
                    String eventType = event.path("type").asText("");
                    
                    // Publish directly to the SSE event bus
                    sseEventBus.publish(channel, eventType, event);
                    
                } catch (Exception e) {
                    log.warn("[{}] Failed to parse MCP event: {}", requestId, line, e);
                    sseEventBus.publish(channel, "parseError", Map.of(
                        "rawLine", line,
                        "error", e.getMessage(),
                        "timestamp", Instant.now().toString()
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("[{}] MCP output stream ended: {}", requestId, e.toString());
        }
    }

    private void handleMcpError(String requestId, java.io.InputStream errorStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.warn("[{}] [MCP stderr] {}", requestId, line);
            }
        } catch (Exception e) {
            log.debug("[{}] MCP error stream ended: {}", requestId, e.toString());
        }
    }

    @Override
    public McpPage fetchPage(String requestId, String source, String query, int page, int pageSize) {
        // Existing implementation for backward compatibility
        Path cwd = Path.of(cfg.getResolvedWorkingDirectory());
        Path python = Path.of(cfg.getFullCommand());

        List<String> cmd = new ArrayList<>();
        cmd.add(python.toString());
        cmd.add("-u");
        cmd.add(cfg.getMainScript());
        if (StringUtils.hasText(source)) { cmd.add("--sources"); cmd.add(source); }
        if (StringUtils.hasText(query)) { cmd.add("--keywords"); cmd.add(query); }
        if (page > 0) { cmd.add("--max-pages"); cmd.add(String.valueOf(page)); }
        if (pageSize > 0) { cmd.add("--per-source-limit"); cmd.add(String.valueOf(pageSize)); }
        cmd.add("--json");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            processRegistry.put(requestId, process);

            log.info("[{}] Exec: {} (cwd={})", requestId, String.join(" ", cmd), cwd);

            // For backward compatibility, return empty page
            // In a real implementation, you'd parse the actual MCP output
            return new McpPage(List.of(), false);
        } catch (Exception e) {
            log.error("[{}] MCP process failed: {}", requestId, e.getMessage(), e);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public JobDetails fetchByUrl(String url) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getFullCommand());
        cmd.add(cfg.getMainScript());
        cmd.add("--json");
        cmd.add("--mode"); cmd.add("parse");
        cmd.add("--url"); cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        String workDir = cfg.getResolvedWorkingDirectory();
        pb.directory(Path.of(workDir).toFile());
        pb.redirectErrorStream(true);

        Process proc = null;
        try {
            log.info("MCP parse exec: wd={} cmd={}", workDir, String.join(" ", cmd));
            proc = pb.start();

            // Make proc effectively final for lambda by assigning to a final variable
            final Process runningProc = proc;

            int timeoutSec = Math.max(5, cfg.getTimeoutSeconds());
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<JobDetails> future = ex.submit(() -> readOneJsonLine(runningProc));
            try {
                JobDetails jd = future.get(timeoutSec, TimeUnit.SECONDS);
                int exit = runningProc.waitFor();
                if (exit != 0) {
                    log.warn("MCP parse nonzero exit={} for url={}", exit, url);
                    if (jd == null) {
                        throw new RuntimeException("MCP parse failed with exit " + exit);
                    }
                }
                return jd;
            } catch (TimeoutException te) {
                runningProc.destroyForcibly();
                throw new RuntimeException("MCP parse timeout after " + timeoutSec + "s");
            } finally {
                ex.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException("MCP parse failed: " + e.getMessage(), e);
        } finally {
            if (proc != null && proc.isAlive()) {
                try { proc.destroyForcibly(); } catch (Exception ignore) {}
            }
        }
    }

    private JobDetails readOneJsonLine(Process proc) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                String type = node.path("type").asText("");
                if ("parsed".equals(type)) {
                    return new JobDetails(
                            node.path("url").asText(null),
                            node.path("title").asText(null),
                            node.path("company").asText(null),
                            node.path("location").isNull() ? null : node.path("location").asText(),
                            node.path("description").isNull() ? null : node.path("description").asText(),
                            node.path("source").isNull() ? null : node.path("source").asText(),
                            node.path("salary").isNull() ? null : node.path("salary").asText(),
                            node.path("team").isNull() ? null : node.path("team").asText()
                    );
                } else if ("parseError".equals(type) || "error".equals(type)) {
                    String url = node.path("url").asText(null);
                    String msg = node.path("error").asText(node.path("message").asText("Unknown parse error"));
                    throw new RuntimeException("MCP parseError for url=" + url + ": " + msg);
                } else if ("banner".equals(type) || "start".equals(type) || "source_start".equals(type)) {
                    continue;
                } else {
                    continue;
                }
            }
            return null;
        }
    }
}