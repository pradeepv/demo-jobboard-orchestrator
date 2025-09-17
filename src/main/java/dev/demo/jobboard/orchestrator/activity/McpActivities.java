package dev.demo.jobboard.orchestrator.activity;

import java.time.Instant;
import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface McpActivities {

  @ActivityMethod
  PageResult fetchPage(String requestId, String source, String query, int page, int pageSize);

  // NEW: Parse a job URL into structured metadata/details
  @ActivityMethod
  ParsedJob parseJobUrl(String requestId, String url);

  // DTOs

  // Minimal ParsedJob DTO (extensible later)
  public static final class ParsedJob {
    public String url;
    public String title;
    public String company;
    public String location;
    public String description;
    public String source;
    public String salary;
    public String team;

    public ParsedJob() {}

    public ParsedJob(String url, String title, String company, String location, String description,
                     String source, String salary, String team) {
      this.url = url;
      this.title = title;
      this.company = company;
      this.location = location;
      this.description = description;
      this.source = source;
      this.salary = salary;
      this.team = team;
    }
  }

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