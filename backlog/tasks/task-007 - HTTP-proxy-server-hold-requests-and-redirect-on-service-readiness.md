---
id: TASK-007
title: 'HTTP proxy server: hold requests and redirect on service readiness'
status: Done
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-13 22:44'
labels:
  - proxy
  - http
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the HTTP proxy server in the `proxy` package using the built-in Sun HTTP Server (`com.sun.net.httpserver.HttpServer`). This is the component that intercepts traffic for scaled-down services.

Flow:
1. Request arrives for a scaled-down service (Doorman is registered as the endpoint)
2. Proxy identifies the target service by matching the **`Host` header + path prefix** against the `routes` list across all managed ScalingPolicies
3. Triggers scale-up (if not already in progress)
4. Blocks the virtual thread waiting for the readiness signal
5. When signal fires → sends HTTP 307 redirect back to the client (same original URL)

A single ScalingPolicy can match multiple host/path combos; all matching requests are held together and released when that one deployment is ready.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An HTTP server listens on a configurable port (default 8080, CLI arg `--proxy-port`)
- [ ] #2 Every incoming request is handled on a virtual thread
- [ ] #3 The target managed service is identified by matching the `Host` header AND the request path prefix against the `routes` list of all active ScalingPolicies (longest path prefix wins in case of overlap)
- [ ] #4 If the service is in `ScaledDown` or `ScalingUp` phase: the request is held (thread blocks) and scale-up is triggered (idempotent)
- [ ] #5 If the service is in `Running` phase: the request is immediately answered with HTTP 307 pointing to the original URL
- [ ] #6 When the readiness signal fires, all threads waiting for that service are unblocked and each sends HTTP 307 `Location: <original URL>` (same scheme, host, path, query string)
- [ ] #7 If scale-up takes longer than `--scale-up-timeout` (default 60s), held requests receive HTTP 503
- [ ] #8 If the Host+path does not match any managed service route, respond with HTTP 404
- [ ] #9 Virtual threads are used (no thread pool sizing needed)
- [ ] #10 Server shuts down cleanly on JVM shutdown hook
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All three subtasks completed:
- **007.01** — `--scale-up-timeout` CLI arg + `DoormanConfig.scaleUpTimeout()` 
- **007.02** — `IngressRouteIndex`: Ingress-based host+path routing table with `HostRoutes` (RW-locked sorted prefix list)
- **007.03** — `ProxyServer`: Sun HTTP Server on virtual threads; 307/503/502/404 responses with real state transitions

130 unit tests pass. Proxy package is complete.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
