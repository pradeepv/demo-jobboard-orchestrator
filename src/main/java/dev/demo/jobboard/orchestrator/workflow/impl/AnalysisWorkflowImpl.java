package dev.demo.jobboard.orchestrator.workflow.impl;

import java.time.Duration;

import dev.demo.jobboard.orchestrator.activity.CrewActivities;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.workflow.AnalysisWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class AnalysisWorkflowImpl implements AnalysisWorkflow {

  private final CrewActivities crew = Workflow.newActivityStub(
      CrewActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          .build());

  private final StreamActivities stream = Workflow.newActivityStub(
      StreamActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .build());

  @Override
  public void start(AnalysisRequest request) {
    String reqId = request.getRequestId();
    String resume = request.getResumeText() == null ? "" : request.getResumeText();

    if (request.getJobIds() == null) return;

    int count = 0;
    for (String jobId : request.getJobIds()) {
      if (count++ >= 5) break; // demo limit
      CrewActivities.AnalysisResult r = crew.analyze(resume, jobId, "Job " + jobId, "Company");
      String payload = "{\"jobId\":\"" + jobId + "\",\"score\":" + r.getScore() + ",\"rationale\":\"" + r.getRationale() + "\"}";
      stream.emit("analyze", reqId, "analysis_progress", payload);
    }
    stream.emit("analyze", reqId, "complete", "{}");
  }
}