package dev.demo.jobboard.orchestrator.workflow;

import java.util.List;

import dev.demo.jobboard.orchestrator.activity.McpActivities.PostingSummary;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CrawlWorkflow {

  /**
   * Kick off a crawl against a given source with a free-text query.
   */
  @WorkflowMethod
  CrawlResult start(CrawlRequest request);

  final class CrawlRequest {
    public List<String> sources;   // e.g., "yc", "hn", "lever", "ashby"
    public String query;    // e.g., "platform engineer"
    public int maxItems;    // cap results to keep history small
    public int startPage;   // optional, defaults to 1

    public CrawlRequest() {}

    public CrawlRequest(List<String> sources, String query, int maxItems, int startPage) {
      this.sources = sources;
      this.query = query;
      this.maxItems = maxItems;
      this.startPage = startPage;
    }

    @Override
    public String toString() {
      return "CrawlRequest{"
          + "sources=" + sources + ", "
          + "query='" + query + "'" + ", "
          + "maxItems=" + maxItems + ", "
          + "startPage=" + startPage + 
          '}';
    }

  }

  final class CrawlResult {
    public List<PostingSummary> items;
    public int lastPageFetched;
    public boolean truncated; // true if hit maxItems cap before hasMore=false

    public CrawlResult() {}

    public CrawlResult(List<PostingSummary> items, int lastPageFetched, boolean truncated) {
      this.items = items;
      this.lastPageFetched = lastPageFetched;
      this.truncated = truncated;
    }

    @Override
    public String toString() {
      return "CrawlResult{" +
          "items=" + (items == null ? 0 : items.size()) +
          ", lastPageFetched=" + lastPageFetched +
          ", truncated=" + truncated +
          '}';
    }
  }
}