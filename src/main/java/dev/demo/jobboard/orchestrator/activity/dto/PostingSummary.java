package dev.demo.jobboard.orchestrator.activity.dto;

public class PostingSummary {
  public String id;       // deterministic id (e.g., hash of URL)
  public String url;
  public String title;
  public String company;
  public String source;   // ats/source key (e.g., lever, ashby, yc)
  public String locale;   // optional

  public PostingSummary() {}

  public PostingSummary(String id, String url, String title, String company, String source, String locale) {
    this.id = id;
    this.url = url;
    this.title = title;
    this.company = company;
    this.source = source;
    this.locale = locale;
  }

  @Override
  public String toString() {
    return "PostingSummary{" +
        "id='" + id + '\'' +
        ", url='" + url + '\'' +
        ", title='" + title + '\'' +
        ", company='" + company + '\'' +
        ", source='" + source + '\'' +
        ", locale='" + locale + '\'' +
        '}';
  }
}