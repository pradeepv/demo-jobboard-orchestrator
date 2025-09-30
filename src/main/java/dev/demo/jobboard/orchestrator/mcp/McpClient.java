package dev.demo.jobboard.orchestrator.mcp;

import java.util.List;

import dev.demo.jobboard.orchestrator.dto.JobDetails;
import dev.demo.jobboard.orchestrator.dto.JobPosting;

public interface McpClient {

    // New: fetch a list of jobs from a given sources and search string 
    // from MCP process
    McpPage fetchPage(String requestId, String source, String query, int page, int pageSize);

    // New: execute a search that streams results through the event bus
    void executeSearch(String requestId, String source, String query, int maxPages, int perSourceLimit);

    // New: fetch single job details by URL via MCP process
    JobDetails fetchByUrl(String url);
    class McpPage {
        public final List<JobPosting> items;
        public final boolean hasMore;

        public McpPage(List<JobPosting> items, boolean hasMore) {
            this.items = items;
            this.hasMore = hasMore;
        }
    }
}