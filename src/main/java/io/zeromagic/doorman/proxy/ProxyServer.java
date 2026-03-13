/*
 * Copyright © 2026 Doorman contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zeromagic.doorman.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.scaling.ScaledApplicationRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP proxy server that intercepts traffic for scaled-down services.
 *
 * <p>For each incoming request:
 * <ol>
 *   <li>Resolve the target service from the {@code Host} header + path via {@link IngressRouteIndex}</li>
 *   <li>Call {@link ScaledApplicationRegistry#awaitReady} — blocks until the service is up or times out</li>
 *   <li>Respond with HTTP 307 (redirect to original URL), 503 (timeout), or 502 (error)</li>
 * </ol>
 */
@Singleton
public class ProxyServer {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

    private final IngressRouteIndex routeIndex;
    private final ScaledApplicationRegistry registry;
    private final DoormanConfig config;
    private HttpServer server;

    @Inject
    public ProxyServer(IngressRouteIndex routeIndex, ScaledApplicationRegistry registry, DoormanConfig config) {
        this.routeIndex = routeIndex;
        this.registry = registry;
        this.config = config;
    }

    /** Test constructor: uses port 0 to get a random available port. */
    ProxyServer(IngressRouteIndex routeIndex, ScaledApplicationRegistry registry, long scaleUpTimeoutMillis) {
        this(routeIndex, registry,
                new DoormanConfig("0.0.0.0", 0, java.time.Duration.ofMillis(scaleUpTimeoutMillis)));
    }

    @PostConstruct
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.proxyPort()), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start proxy server on port " + config.proxyPort(), e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
        LOG.info("ProxyServer listening on port {}", config.proxyPort());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("ProxyServer stopped");
        }
    }

    /** Exposed for testing — returns the actual bound port (useful when port 0 is used). */
    int boundPort() {
        return server.getAddress().getPort();
    }

    // ── Request handler ───────────────────────────────────────────────────────

    private void handle(HttpExchange exchange) {
        try (exchange) {
            String hostHeader = exchange.getRequestHeaders().getFirst("Host");
            // Strip port from Host header for route lookup (e.g. "example.test:12345" → "example.test")
            String host = hostHeader != null && hostHeader.contains(":")
                    ? hostHeader.substring(0, hostHeader.lastIndexOf(':'))
                    : hostHeader;
            String path = exchange.getRequestURI().getPath();

            Optional<RouteTarget> route = routeIndex.resolve(host, path);
            if (route.isEmpty()) {
                respond(exchange, 404, "Not Found");
                return;
            }

            var target = route.get();
            var future = registry.awaitReady(target.namespace(), target.serviceName());
            try {
                future.get(config.scaleUpTimeout().toMillis(), TimeUnit.MILLISECONDS);
                String location = buildLocation(exchange, hostHeader);
                exchange.getResponseHeaders().set("Location", location);
                respond(exchange, 307, "");
            } catch (TimeoutException e) {
                LOG.warn("Scale-up timeout for {}/{}", target.namespace(), target.serviceName());
                respond(exchange, 503, "Service Unavailable");
            } catch (Exception e) {
                LOG.warn("awaitReady failed for {}/{}: {}", target.namespace(), target.serviceName(), e.getMessage());
                respond(exchange, 502, "Bad Gateway");
            }
        } catch (IOException e) {
            LOG.error("Error handling request", e);
        }
    }

    private static String buildLocation(HttpExchange exchange, String host) {
        String scheme = Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"))
                .orElse("http");
        var uri = exchange.getRequestURI();
        return scheme + "://" + host + uri.getRawPath()
                + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(status, bytes.length > 0 ? bytes.length : -1);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
