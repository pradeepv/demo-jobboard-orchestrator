package dev.demo.jobboard.orchestrator.workflow;

import dev.demo.jobboard.orchestrator.workflow.model.CrawlRequest;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CrawlWorkflow {
  @WorkflowMethod
  void start(CrawlRequest request);
}