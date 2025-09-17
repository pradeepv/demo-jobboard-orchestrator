package dev.demo.jobboard.orchestrator.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.demo.jobboard.orchestrator.config.McpConfig;
import dev.demo.jobboard.orchestrator.dto.JobDetails;

public class McpClientProcess implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClientProcess.class);
    private final McpConfig cfg;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpClientProcess(McpConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public McpPage search(String query, int page, int pageSize) {
        // Not wired yet for search; return empty page.
        return new McpPage(List.of(), false);
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