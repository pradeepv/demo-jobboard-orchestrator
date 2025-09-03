package dev.demo.jobboard.orchestrator.dto;

public class CrawlRequest {
    private String requestId;
    private String source;     // e.g., "MCP"
    private String query;
    private int maxItems;
    private int startPage;
    private int pageSize;

    public CrawlRequest() {}

    public CrawlRequest(String requestId, String source, String query, int maxItems, int startPage, int pageSize) {
        this.requestId = requestId;
        this.source = source;
        this.query = query;
        this.maxItems = maxItems;
        this.startPage = startPage;
        this.pageSize = pageSize;
    }

    public String getRequestId() { return requestId; }
    public String getSource() { return source; }
    public String getQuery() { return query; }
    public int getMaxItems() { return maxItems; }
    public int getStartPage() { return startPage; }
    public int getPageSize() { return pageSize; }

    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setSource(String source) { this.source = source; }
    public void setQuery(String query) { this.query = query; }
    public void setMaxItems(int maxItems) { this.maxItems = maxItems; }
    public void setStartPage(int startPage) { this.startPage = startPage; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}