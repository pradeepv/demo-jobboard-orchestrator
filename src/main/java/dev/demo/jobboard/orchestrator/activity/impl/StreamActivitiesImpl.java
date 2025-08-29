package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.StreamActivities;

public class StreamActivitiesImpl implements StreamActivities {
  @Override
  public void emit(String channel, String requestId, String type, String jsonPayload) {
    // Stub: no-op. In your blog demo, call your API /api/emit here.
    System.out.println("[EMIT] channel=" + channel + " requestId=" + requestId + " type=" + type + " payload=" + jsonPayload);
  }
}