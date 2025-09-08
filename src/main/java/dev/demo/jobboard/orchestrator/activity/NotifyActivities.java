package dev.demo.jobboard.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotifyActivities {
  @ActivityMethod
  void sourceComplete(String requestId, String source);
}