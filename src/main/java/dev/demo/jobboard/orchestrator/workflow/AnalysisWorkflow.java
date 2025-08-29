package dev.demo.jobboard.orchestrator.workflow;

import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AnalysisWorkflow {
  @WorkflowMethod
  void start(AnalysisRequest request);
}