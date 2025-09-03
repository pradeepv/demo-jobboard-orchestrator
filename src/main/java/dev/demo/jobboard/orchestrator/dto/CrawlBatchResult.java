package dev.demo.jobboard.orchestrator.dto;

import java.util.List;

public class CrawlBatchResult {
    private int page;
    private boolean hasMore;
    private List<JobPosting> items;

    public CrawlBatchResult() {}

    public CrawlBatchResult(int page, boolean hasMore, List<JobPosting> items) {
        this.page = page;
        this.hasMore = hasMore;
        this.items = items;
    }

    public int getPage() { return page; }
    public boolean isHasMore() { return hasMore; }
    public List<JobPosting> getItems() { return items; }

    public void setPage(int page) { this.page = page; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    public void setItems(List<JobPosting> items) { this.items = items; }
}