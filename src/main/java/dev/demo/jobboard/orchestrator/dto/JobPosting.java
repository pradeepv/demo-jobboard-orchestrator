package dev.demo.jobboard.orchestrator.dto;

import java.time.Instant;
import java.util.Objects;

public class JobPosting {
    private String id;
    private String title;
    private String company;
    private String location;
    private String url;
    private String source;
    private Instant postedAt;
    private String snippet;

    public JobPosting() {}

    public JobPosting(String id, String title, String company, String location, String url, String source, Instant postedAt, String snippet) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.location = location;
        this.url = url;
        this.source = source;
        this.postedAt = postedAt;
        this.snippet = snippet;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public String getUrl() { return url; }
    public String getSource() { return source; }
    public Instant getPostedAt() { return postedAt; }
    public String getSnippet() { return snippet; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setCompany(String company) { this.company = company; }
    public void setLocation(String location) { this.location = location; }
    public void setUrl(String url) { this.url = url; }
    public void setSource(String source) { this.source = source; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobPosting)) return false;
        JobPosting that = (JobPosting) o;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}