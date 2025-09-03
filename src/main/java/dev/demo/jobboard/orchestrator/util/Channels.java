package dev.demo.jobboard.orchestrator.util;

public final class Channels {
    private Channels() {}

    public static String forRequest(String requestId) {
        // Single place to change if you ever adjust the prefixing
        return "req:" + requestId;
    }
}