package dev.demo.jobboard.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CrewActivities {
  @ActivityMethod
  AnalysisResult analyze(
    String resumeText,
    String jobId,
    String jobTitle,
    String company
  );

  class AnalysisResult implements java.io.Serializable {

    private double score;
    private String rationale;

    public AnalysisResult() {}

    public AnalysisResult(double score, String rationale) {
      this.score = score;
      this.rationale = rationale;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    public String getRationale() {
      return rationale;
    }

    public void setRationale(String rationale) {
      this.rationale = rationale;
    }
  }
}
