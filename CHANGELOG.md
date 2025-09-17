# Changelog

All notable changes to this Temporal Java Spring Boot orchestrator demo will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added

- CrawlWorkflow and AnalysisWorkflow with corresponding implementations.
- Activity interfaces and stub implementations:
  - McpActivities (+ Impl) for mock job search.
  - CrewActivities (+ Impl) for heuristic resume-to-job scoring.
  - StreamActivities (+ Impl) for emitting streaming events (stubbed to stdout).
- Data models:
  - CrawlRequest using List<String> roles.
  - AnalysisRequest with requestId, resumeText, jobIds.
  - JobPosting minimal shape.
- Temporal configuration:
  - WorkflowServiceStubs, WorkflowClient, WorkerFactory beans.
  - Single task queue demo-tq.
- REST controller endpoints:
  - POST /api/crawl to start CrawlWorkflow.
  - POST /api/analyze to start AnalysisWorkflow.
- Worker registration for demo workflows and activities.
- Application bootstrap class.
- Server port override guidance to avoid conflict with Temporal UI.

### Changed

- Roles type updated from String to List<String> across the codebase:
  - CrawlRequest roles field and constructor.
  - WorkflowController /crawl endpoint to accept List<String> roles (or split comma-separated string).
  - CrawlWorkflowImpl to join roles for query generation.
- Consolidated worker setup to use a single demo worker on demo-tq instead of the previous PAYMENT_TASK_QUEUE.
- Updated controller to reference TemporalConfig.TASK_QUEUE.

### Removed

- Payment-specific wiring from WorkerRegistration (optional; retained only if you choose Option B to run both workers).
- Any constructor/usage paths expecting roles as a String.

### Fixed

- Compilation errors caused by String vs List<String> mismatch in WorkflowController and CrawlWorkflowImpl.
- Port clash with Temporal UI (8080) by documenting server.port=8081 and run-time overrides.

## [1.0.0] - 2025-08-26

### Added

- Initial Temporal Spring Boot project scaffolding.
- Payment example workflow and activities (for initial testing).
- WorkerRegistration for PAYMENT_TASK_QUEUE.
- Basic application.properties.

[Unreleased]: https://example.com/compare/v1.0.0...HEAD
[1.0.0]: https://example.com/releases/v1.0.0

## 2.0.0 - 2025-07-17

- Added robust SSE headers and heartbeats to improve reliability across proxies and browsers.
    - Set Cache-Control: no-cache, no-transform and Connection: keep-alive on /api/stream/* and /api/local/stream/*.
    - Added initial connected event on subscribe and periodic comment heartbeats (ping …) to keep connections alive.
  - Introduced SseEventBus with subscription lifecycle management and heartbeat scheduling.
  - WorkflowController
    - Stream endpoint now sets SSE headers and delegates to SseEventBus.subscribe(response).
    - No functional change to workflow start; still publishes initial analysis event.
  - McpRunnerController
    - Local stream endpoint now sets SSE headers and emits periodic heartbeats.
    - Sends an initial connected event upon subscription.
  - Configuration
    - Added SseConfig bean to provide a singleton SseEventBus (moveable to existing config package).
  - Notes
    - If running behind nginx, disable buffering for SSE routes (proxy_buffering off; add_header X-Accel-Buffering no;).
    - Ensure server compression excludes text/event-stream.