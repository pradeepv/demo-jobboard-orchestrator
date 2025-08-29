package dev.demo.jobboard.orchestrator.workflow.model;

import java.io.Serializable;
import java.util.List;

public class CrawlRequest implements Serializable {
  private String requestId;
  private List<String> roles;

  public CrawlRequest() {}

  public CrawlRequest(String requestId, List<String> roles) {
    this.requestId = requestId;
    this.roles = roles;
  }

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }

  public List<String> getRoles() { return roles; }
  public void setRoles(List<String> roles) { this.roles = roles; }
}