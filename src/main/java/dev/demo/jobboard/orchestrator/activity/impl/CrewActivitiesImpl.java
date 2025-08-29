package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.CrewActivities;

public class CrewActivitiesImpl implements CrewActivities {
  @Override
  public AnalysisResult analyze(String resumeText, String jobId, String jobTitle, String company) {
    // Stub scoring: simple keyword presence
    double score = 0.3;
    if (resumeText != null) {
      if (resumeText.toLowerCase().contains("java")) score += 0.3;
      if (resumeText.toLowerCase().contains("backend")) score += 0.2;
      if (resumeText.toLowerCase().contains("temporal")) score += 0.2;
    }
    String rationale = "Heuristic score for " + jobTitle + " @ " + company;
    return new AnalysisResult(Math.min(score, 1.0), rationale);
  }
}