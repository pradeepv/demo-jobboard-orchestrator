package dev.demo.jobboard.orchestrator.activity;

import java.util.List;

import dev.demo.jobboard.orchestrator.workflow.model.JobPosting;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface McpActivities {
  @ActivityMethod
  List<JobPosting> mcpSearch(String source, String query, int page);
}