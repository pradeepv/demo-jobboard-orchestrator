package dev.demo.jobboard.orchestrator.dto;

import java.util.Objects;

public class JobDetails {
    private String url;
    private String title;
    private String company;
    private String location;     // nullable
    private String description;  // nullable
    private String source;       // nullable (hostname)
    private String salary;       // nullable
    private String team;         // nullable

    public JobDetails() {}

    public JobDetails(String url, String title, String company, String location, String description, String source, String salary, String team) {
        this.url = url;
        this.title = title;
        this.company = company;
        this.location = location;
        this.description = description;
        this.source = source;
        this.salary = salary;
        this.team = team;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    @Override
    public String toString() {
        return "JobDetails{" +
                "url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", company='" + company + '\'' +
                ", location='" + location + '\'' +
                ", source='" + source + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobDetails)) return false;
        JobDetails that = (JobDetails) o;
        return Objects.equals(url, that.url) &&
               Objects.equals(title, that.title) &&
               Objects.equals(company, that.company) &&
               Objects.equals(location, that.location) &&
               Objects.equals(description, that.description) &&
               Objects.equals(source, that.source) &&
               Objects.equals(salary, that.salary) &&
               Objects.equals(team, that.team);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, title, company, location, description, source, salary, team);
    }
}
