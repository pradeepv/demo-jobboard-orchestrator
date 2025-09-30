package dev.demo.jobboard.orchestrator.activity;

import java.util.List;

import dev.demo.jobboard.orchestrator.workflow.CrawlWorkflow;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface McpActivities {

  @ActivityMethod
  void executeSearch(String requestId, String source, String query, int maxPages, int perSourceLimit);

  @ActivityMethod
  PageResult fetchPage(String requestId, String source, String query, int page, int pageSize);

  @ActivityMethod
  ParsedJob parseJobUrl(String requestId, String url);

  // Data classes
  final class PageResult {
    public final int page;
    public final boolean hasMore;
    public final List<PostingSummary> items;

    public PageResult(int page, boolean hasMore, List<PostingSummary> items) {
      this.page = page;
      this.hasMore = hasMore;
      this.items = items;
    }
  }

  final class PostingSummary {
    public final String id;
    public final String title;
    public final String company;
    public final String location;
    public final String url;
    public final String source;
    public final java.time.Instant postedAt;
    public final String snippet;

    public PostingSummary(String id, String title, String company, String location, String url, String source, java.time.Instant postedAt, String snippet) {
      this.id = id;
      this.title = title;
      this.company = company;
      this.location = location;
      this.url = url;
      this.source = source;
      this.postedAt = postedAt;
      this.snippet = snippet;
    }
  }

  final class ParsedJob {
    public String url;
    public String source;
    public String title;
    public String company;
    public String location;
    public String salary;
    public String team;
    public String description;
  }
}