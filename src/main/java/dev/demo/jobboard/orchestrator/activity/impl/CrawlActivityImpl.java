package dev.demo.jobboard.orchestrator.activity.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import dev.demo.jobboard.orchestrator.activity.CrawlActivity;
import dev.demo.jobboard.orchestrator.dto.CrawlBatchResult;
import dev.demo.jobboard.orchestrator.dto.CrawlRequest;
import dev.demo.jobboard.orchestrator.mcp.McpClient;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;

public class CrawlActivityImpl implements CrawlActivity {

    private final McpClient mcpClient;
    private final SseEventBus bus;

    public CrawlActivityImpl(McpClient mcpClient, SseEventBus bus) {
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

        McpClient.McpPage result = mcpClient.search(req.getQuery(), page, req.getPageSize());

        Map<String, Object> payload = new HashMap<>();
        payload.put("stage", "page");
        payload.put("page", page);
        payload.put("count", result.items.size());
        payload.put("ts", Instant.now().toString());
        bus.publish(channel, "crawl", payload);

        return new CrawlBatchResult(page, result.hasMore, result.items);
    }

    private static String channel(String requestId) {
        return "req:" + requestId;
    }
}