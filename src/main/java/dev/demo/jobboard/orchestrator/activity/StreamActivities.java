package dev.demo.jobboard.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface StreamActivities {
  @ActivityMethod
  void emit(String channel, String requestId, String type, String jsonPayload);
}