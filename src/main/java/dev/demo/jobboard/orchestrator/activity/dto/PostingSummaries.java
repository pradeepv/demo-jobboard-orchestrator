package dev.demo.jobboard.orchestrator.activity.dto;

import java.util.List;

public class PostingSummaries {
  public List<PostingSummary> items;
  public int page;
  public boolean hasMore;

  public PostingSummaries() {}

  public PostingSummaries(List<PostingSummary> items, int page, boolean hasMore) {
    this.items = items;
    this.page = page;
    this.hasMore = hasMore;
  }

  @Override
  public String toString() {
    return "PostingSummaries{" +
        "items=" + (items == null ? 0 : items.size()) +
        ", page=" + page +
        ", hasMore=" + hasMore +
        '}';
  }
}