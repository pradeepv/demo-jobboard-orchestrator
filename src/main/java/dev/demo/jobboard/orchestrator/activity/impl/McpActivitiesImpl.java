package dev.demo.jobboard.orchestrator.activity.impl;

import java.util.ArrayList;
import java.util.List;

import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.workflow.model.JobPosting;

public class McpActivitiesImpl implements McpActivities {
  @Override
  public List<JobPosting> mcpSearch(String source, String query, int page) {
    // Stub: return a few fake jobs so the demo compiles and runs
    List<JobPosting> list = new ArrayList<>();
    list.add(new JobPosting(source + "-p" + page + "-1", "Software Engineer (" + query + ")", "Acme"));
    list.add(new JobPosting(source + "-p" + page + "-2", "Backend Developer", "Globex"));
    return list;
  }
}