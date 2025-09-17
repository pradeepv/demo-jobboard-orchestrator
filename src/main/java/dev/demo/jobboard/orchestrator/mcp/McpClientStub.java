package dev.demo.jobboard.orchestrator.mcp;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.demo.jobboard.orchestrator.dto.JobDetails;
import dev.demo.jobboard.orchestrator.dto.JobPosting;

public class McpClientStub implements McpClient {

    @Override
    public McpPage search(String query, int page, int pageSize) {
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

    @Override
    public JobDetails fetchByUrl(String url) {
        // Synthetic parsing mirroring Python main.py --mode parse
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String lastSeg = path.endsWith("/") ? path.substring(0, Math.max(0, path.length() - 1)) : path;
            int idx = lastSeg.lastIndexOf('/');
            String slug = idx >= 0 ? lastSeg.substring(idx + 1) : lastSeg;
            String[] parts = host.split("\\.");
            String company = parts.length >= 2 ? parts[parts.length - 2] : host;

            return new JobDetails(
                    url,
                    (slug == null || slug.isEmpty()) ? "Job Posting" : "Job: " + slug,
                    (company == null || company.isEmpty()) ? "Unknown" : company,
                    null,
                    null,
                    host.isEmpty() ? null : host,
                    null,
                    null
            );
        } catch (Exception e) {
            // In stub, return a minimal object instead of throwing
            return new JobDetails(url, "Job Posting", "Unknown", null, null, null, null, null);
        }
    }
}