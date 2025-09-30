package dev.demo.jobboard.orchestrator.activity.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import dev.demo.jobboard.orchestrator.activity.CrawlActivities;
import dev.demo.jobboard.orchestrator.dto.CrawlBatchResult;
import dev.demo.jobboard.orchestrator.dto.CrawlRequest;
import dev.demo.jobboard.orchestrator.mcp.McpClient;
import dev.demo.jobboard.orchestrator.config.McpConfig;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import org.springframework.beans.factory.annotation.Autowired;

public class CrawlActivitiesImpl implements CrawlActivities {

    @Autowired
    private McpConfig mcpConfig;    
    private final McpClient mcpClient;
    private final SseEventBus bus;

    public CrawlActivitiesImpl(McpClient mcpClient, SseEventBus bus) {
        this.mcpClient = mcpClient;
        this.bus = bus;
    }

    @Override
    public CrawlBatchResult fetchBatch(CrawlRequest req, int page) {
        String channel = channel(req.getRequestId());
        // Emit a start-of-batch event
        bus.publish(channel, "crawl", Map.of(
                "stage", "startPage",
                "page", page,
                "ts", Instant.now().toString()
        ));

        McpClient.McpPage result = mcpConfig.isEnableDirectExecution() ? mcpClient.fetchPage(req.getRequestId(), req.getSource(), req.getQuery(), page, req.getPageSize()) : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("stage", "page");
        payload.put("page", page);
        payload.put("count", result == null ? 0 : result.items.size());
        payload.put("ts", Instant.now().toString());
        bus.publish(channel, "crawl", payload);

        return new CrawlBatchResult(page, result == null ? false : result.hasMore, result == null ? List.of() : result.items);
    }

    private static String channel(String requestId) {
        return "req:" + requestId;
    }
}