package dev.demo.jobboard.orchestrator.mcp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.demo.jobboard.orchestrator.dto.JobPosting;

public class McpClientStub implements McpClient {

    @Override
    public McpPage search(String query, int page, int pageSize) {
        // Simulate 5 pages max, last page may be partial
        int maxPages = 5;
        if (page > maxPages) {
            return new McpPage(List.of(), false);
        }
        int count = page == maxPages ? Math.max(1, pageSize / 2) : pageSize;
        List<JobPosting> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString();
            list.add(new JobPosting(
                    id,
                    "Software Engineer " + (i + 1) + " [" + query + "]",
                    "Acme Corp",
                    "Remote",
                    "https://jobs.example.com/" + id,
                    "MCP-STUB",
                    Instant.now(),
                    "Great role working on " + query
            ));
        }
        boolean hasMore = page < maxPages;
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        return new McpPage(list, hasMore);
    }
}