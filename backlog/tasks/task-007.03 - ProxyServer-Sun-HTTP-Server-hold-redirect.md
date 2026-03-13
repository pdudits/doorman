---
id: TASK-007.03
title: 'ProxyServer: Sun HTTP Server, hold + redirect'
status: To Do
assignee: []
created_date: '2026-03-13 20:27'
labels:
  - proxy
  - http
milestone: m-0
dependencies:
  - TASK-007.01
  - TASK-007.02
parent_task_id: TASK-007
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The main proxy implementation using Sun HTTP Server and virtual threads.

**Scope:**
1. New `@Singleton` class `ProxyServer` in `proxy/`:
   - `@Inject` constructor: `IngressRouteIndex routeIndex, ScaledApplicationRegistry registry, DoormanConfig config`
   - `start()` method: create `HttpServer.create(new InetSocketAddress(config.proxyPort()), 0)`, set executor to `Executors.newVirtualThreadPerTaskExecutor()`, register handler on `"/"`, call `server.start()`
   - JVM shutdown hook: `Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)))`

2. **Per-request handler logic:**
   ```
   host  = exchange.getRequestHeaders().getFirst("Host")  // strip :port if present
   path  = exchange.getRequestURI().getPath()
   route = routeIndex.resolve(host, path)
   if (route.isEmpty()) → send 404, return

   future = registry.awaitReady(route.namespace(), route.serviceName())
   try {
       future.get(config.scaleUpTimeout().toMillis(), TimeUnit.MILLISECONDS)
       location = reconstruct URL from request (scheme from X-Forwarded-Proto or "http", host, path, query)
       send 307 with Location header
   } catch (TimeoutException) {
       send 503
   } catch (Exception) {
       send 502
   }
   ```

3. **307 URL reconstruction:** Use `exchange.getRequestURI()` (has path+query), prepend scheme+host. Scheme: check `X-Forwarded-Proto` header first, default to `"http"`.

4. **Wire into startup:** call `proxyServer.start()` from `Main.java` (or via DI `@PostConstruct` / init pattern matching existing code style).

5. **Unit tests** (no k3s needed — use stub/mock objects):
   - 307 immediate: route found, `registry.awaitReady()` returns already-completed future
   - 307 after wait: route found, future completed by another thread mid-test
   - 503 timeout: future never completes within timeout
   - 404: `routeIndex.resolve()` returns empty
   - 502: future completes exceptionally

6. **IT test:** If the scenario can be exercised in k3s within reasonable scope, add it. Otherwise create a TASK-007.04 backlog entry describing the end-to-end scenario.

**Key files:**
- new `src/main/java/io/zeromagic/doorman/proxy/ProxyServer.java`
- `src/main/java/io/zeromagic/doorman/Main.java` — wire `proxyServer.start()`
- new `src/test/java/io/zeromagic/doorman/proxy/ProxyServerTest.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 HTTP server listens on DoormanConfig.proxyPort() using virtual threads
- [ ] #2 Unknown Host+path returns 404
- [ ] #3 ScaledDown/ScalingUp service: request blocks, scale-up triggers, 307 Location on ready
- [ ] #4 Running service: immediate 307 Location to original URL (same scheme/host/path/query)
- [ ] #5 Scale-up timeout exceeded: 503 response
- [ ] #6 Failed future (ScalingDown/Stopped/missing): 502 response
- [ ] #7 307 Location preserves original scheme, host, path, and query string
- [ ] #8 Server stops cleanly on JVM shutdown hook
- [ ] #9 Unit tests cover: 307-immediate, 307-after-wait, 503-timeout, 404-unknown, 502-failed-future
- [ ] #10 IT test in k3s or a dedicated backlog task created for the scenario
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
