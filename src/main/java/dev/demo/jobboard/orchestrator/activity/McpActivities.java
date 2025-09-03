package dev.demo.jobboard.orchestrator.activity;

import java.time.Instant;
import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface McpActivities {

  @ActivityMethod
  PageResult fetchPage(String requestId, String source, String query, int page, int pageSize);

  // Your PostingSummary was referenced from this package; reusing the signature you mentioned
  public static final class PostingSummary {
    public String id;
    public String title;
    public String company;
    public String location;
    public String url;
    public String source;
    public Instant postedAt;
    public String snippet;

    public PostingSummary() {}

    public PostingSummary(String id, String title, String company, String location, String url, String source, Instant postedAt, String snippet) {
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

  public static final class PageResult {
    public int page;
    public boolean hasMore;
    public List<PostingSummary> items;

    public PageResult() {}
    public PageResult(int page, boolean hasMore, List<PostingSummary> items) {
      this.page = page;
      this.hasMore = hasMore;
      this.items = items;
    }
  }
}