# Context: SSE event bus + workflow integration

  Purpose
  - Orchestrator exposes an SSE bus for crawl progress; workflows/activities emit events to a per-request channel.

  Channeling
  - Single source of truth: Channels.forRequest(requestId) -> "req:" + requestId.
  - Always use the full requestId across controller, workflow, activities, and debug endpoints.

  SSE bus (SseEventBus)
  - addSubscriber(channel): registers an SseEmitter and immediately sends event "connected".
  - publish(channel, eventName, payload): sends event (we use eventName="crawl" for domain events).
  - complete(channel, requestId): sends a final "done" event and closes all emitters; clears channel state.
  - Keepalive: scheduled "ping" every 15s to prevent proxy timeouts (requires @EnableScheduling).

  API surface
  - POST /api/crawl?roles=... -> { requestId, sseUrl }
    - Generates requestId, emits optional crawlStart, and should start the workflow with requestId.
  - GET /api/stream/{requestId} -> text/event-stream
    - Subscribes client to channel derived from requestId, sends "connected", then pings periodically.
  - Debug helpers:
    - POST /api/debug/crawl/send?id={requestId}&msg=... -> publishes a "page" under event "crawl".
    - POST /api/debug/crawl/complete?id={requestId} -> publishes "crawlComplete" then complete().

  Event schema (payload for event name "crawl")
  - { kind: "crawlStart", payload: { query/roles, ts? } }
  - { kind: "page", payload: { id, title, url, company, location, createdAt, description, tags } }
  - { kind: "crawlComplete", payload: { totalItems?, ts? } }
  - { kind: "error", payload: { message, code?, ts? } }

  Workflow wiring expectations
  - Controller generates requestId and starts the workflow with it (plus roles/query).
  - Activities/services publish via:
    - bus.publish(Channels.forRequest(requestId), "crawl", eventPayload)
  - On finish (or failure):
    - Publish "crawlComplete" or "error" and then call:
      - bus.complete(Channels.forRequest(requestId), requestId)

  Config
  - Scheduling: @EnableScheduling enabled (e.g., AppConfig) to drive keepalive pings.
  - CORS/Proxy:
    - Ensure SSE endpoint is allowed for GET with credentials if cross-origin.
    - If behind nginx/Traefik: disable buffering for /api/stream, and increase read timeouts.

  Operational notes
  - If there are no subscribers, publish() is a no-op (logged at debug).
  - complete() is idempotent for our purposes; calling when no subs is safe.
  - Log lines include channel and requestId for traceability.

  Testing recipe
  1) POST /api/crawl?roles=engineer -> capture { requestId, sseUrl }.
  2) curl -N {sseUrl} in one terminal; observe "connected" and periodic "ping".
  3) POST /api/debug/crawl/send?id={requestId}&msg=Hello -> observe "crawl" event with kind=page.
  4) POST /api/debug/crawl/complete?id={requestId} -> observe crawlComplete, then stream closes.
