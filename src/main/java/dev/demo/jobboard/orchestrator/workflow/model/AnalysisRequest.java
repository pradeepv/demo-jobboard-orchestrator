package dev.demo.jobboard.orchestrator.workflow.model;

import java.io.Serializable;
import java.util.List;

public class AnalysisRequest implements Serializable {
  private String requestId;
  private String resumeText;
  private List<String> jobIds;

  public AnalysisRequest() {}

  public AnalysisRequest(String requestId, String resumeText, List<String> jobIds) {
    this.requestId = requestId;
    this.resumeText = resumeText;
    this.jobIds = jobIds;
  }

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }

  public String getResumeText() { return resumeText; }
  public void setResumeText(String resumeText) { this.resumeText = resumeText; }

  public List<String> getJobIds() { return jobIds; }
  public void setJobIds(List<String> jobIds) { this.jobIds = jobIds; }
}