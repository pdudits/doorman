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

package io.zeromagic.doorman.traffic;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.zeromagic.doorman.cli.DurationParser;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.repository.ScaledApplication;
import io.zeromagic.doorman.repository.ScaledApplicationRegistry;
import io.zeromagic.doorman.repository.ServiceState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled polling component that detects idle services and triggers scale-down.
 *
 * <p>On each poll cycle:
 * <ol>
 *   <li>Scrapes Traefik metrics via {@link MetricsScraper}.</li>
 *   <li>For every {@link ServiceState.Running} service in the registry, resolves its
 *       Traefik service label and reads the {@code traefik_service_requests_total} counter.</li>
 *   <li>Applies idle tracking: resets the idle timer on traffic, starts it on silence,
 *       and calls {@link ScaledApplicationRegistry#beginScalingDown} when idle duration
 *       exceeds the effective timeout ({@link ScaledApplication.Snapshot#idleTimeout()}).</li>
 * </ol>
 *
 * <p>A counter-reset guard handles Traefik pod restarts: if the current counter is lower
 * than the last observed value it is treated as traffic (baseline reset, idle timer cleared).
 */
@Singleton
public class IdleDetector {

    private static final Logger LOG = LoggerFactory.getLogger(IdleDetector.class);
    static final String METRIC_NAME = "traefik_service_requests_total";
    static final String SERVICE_LABEL = "service";

    /** Per-service idle tracking state. */
    record IdleState(double lastCounter, Instant idleSince) {}

    private final MetricsScraper scraper;
    private final TraefikServiceNameResolver resolver;
    private final ScaledApplicationRegistry registry;
    private final Duration pollInterval;
    private final Clock clock;

    private final ConcurrentHashMap<String, IdleState> states = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Thread pollThread;

    /** Production constructor — uses system clock. */
    @Inject
    public IdleDetector(MetricsScraper scraper,
                        TraefikServiceNameResolver resolver,
                        ScaledApplicationRegistry registry,
                        TraefikConfig config) {
        this(scraper, resolver, registry, config, Clock.systemUTC());
    }

    /** Test constructor — accepts a controllable clock. */
    IdleDetector(MetricsScraper scraper,
                 TraefikServiceNameResolver resolver,
                 ScaledApplicationRegistry registry,
                 TraefikConfig config,
                 Clock clock) {
        this.scraper = scraper;
        this.resolver = resolver;
        this.registry = registry;
        this.pollInterval = DurationParser.parse(config.metricsPollInterval());
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        pollThread = Thread.ofVirtual().name("idle-detector").start(this::loop);
        LOG.info("IdleDetector started (pollInterval={})", pollInterval);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        LOG.info("IdleDetector stopped");
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(pollInterval.toMillis());
                doPoll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.warn("Unexpected error during idle detection poll: {}", e.getMessage(), e);
            }
        }
    }

    void doPoll() {
        Map<String, Double> counts = new HashMap<>();
        scraper.scrape(sample -> {
            if (METRIC_NAME.equals(sample.metricName())) {
                String svc = sample.labels().get(SERVICE_LABEL);
                if (svc != null) {
                    counts.merge(svc, sample.value(), Double::sum);
                }
            }
        });
        evaluateAll(counts);
    }

    /**
     * Evaluates idle state for all registered applications against the given counter snapshot.
     * Package-private for direct use in tests (avoids real scraping and scheduling).
     */
    void evaluateAll(Map<String, Double> counts) {
        for (ScaledApplication app : registry.all()) {
            if (!(app.currentState() instanceof ServiceState.Running)) {
                states.remove(stateKey(app.snapshot()));
                continue;
            }
            try {
                evaluateOne(app, counts);
            } catch (Exception e) {
                LOG.warn("Error evaluating idle state for {}/{}: {}",
                        app.snapshot().namespace(), app.snapshot().serviceName(), e.getMessage());
            }
        }
    }

    private void evaluateOne(ScaledApplication app, Map<String, Double> counts) {
        var snap = app.snapshot();
        var label = resolver.resolve(snap.namespace(), snap.serviceName());
        if (label.isEmpty()) {
            LOG.debug("Could not resolve Traefik label for {}/{} — skipping this cycle",
                    snap.namespace(), snap.serviceName());
            return;
        }

        double current = counts.getOrDefault(label.get(), 0.0);
        var key = stateKey(snap);
        var prev = states.get(key);

        if (prev == null) {
            // First observation — establish baseline; do not trigger scale-down yet
            states.put(key, new IdleState(current, null));
            return;
        }

        // Counter-reset guard: Traefik pod restart resets the counter
        if (current < prev.lastCounter()) {
            LOG.debug("Counter reset detected for {}/{} (was={} now={}); resetting baseline",
                    snap.namespace(), snap.serviceName(), prev.lastCounter(), current);
            states.put(key, new IdleState(current, null));
            return;
        }

        double delta = current - prev.lastCounter();
        Instant now = clock.instant();

        if (delta > 0) {
            states.put(key, new IdleState(current, null));
            return;
        }

        // delta == 0: no new traffic
        Instant idleSince = prev.idleSince() != null ? prev.idleSince() : now;
        states.put(key, new IdleState(current, idleSince));

        Duration idleDuration = Duration.between(idleSince, now);
        if (idleDuration.compareTo(snap.idleTimeout()) >= 0) {
            LOG.info("Service {}/{} idle for {} (>= {}); initiating scale-down",
                    snap.namespace(), snap.serviceName(), idleDuration, snap.idleTimeout());
            registry.beginScalingDown(snap.namespace(), snap.serviceName());
        }
    }

    private static String stateKey(ScaledApplication.Snapshot snap) {
        return snap.namespace() + "/" + snap.serviceName();
    }
}
