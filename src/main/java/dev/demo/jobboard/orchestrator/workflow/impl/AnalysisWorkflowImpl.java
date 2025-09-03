package dev.demo.jobboard.orchestrator.workflow.impl;

import dev.demo.jobboard.orchestrator.workflow.AnalysisWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import io.temporal.workflow.Workflow;

public class AnalysisWorkflowImpl implements AnalysisWorkflow {

  @Override
  public void start(AnalysisRequest request) {
    Workflow.getLogger(getClass().getSimpleName())
        .info("Starting analysis for requestId={} ({} jobIds)",
            request.getRequestId(),
            request.getJobIds() == null ? 0 : request.getJobIds().size());
    // Do work; no return
  }
}