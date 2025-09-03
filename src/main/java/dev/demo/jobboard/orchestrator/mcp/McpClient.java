package dev.demo.jobboard.orchestrator.mcp;

import java.util.List;

import dev.demo.jobboard.orchestrator.dto.JobPosting;

public interface McpClient {
    // Replace with your real signature as needed
    McpPage search(String query, int page, int pageSize);

    class McpPage {
        public final List<JobPosting> items;
        public final boolean hasMore;

        public McpPage(List<JobPosting> items, boolean hasMore) {
            this.items = items;
            this.hasMore = hasMore;
        }
    }
}