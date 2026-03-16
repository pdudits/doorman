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

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

/**
 * Scrapes metrics from all endpoints provided by a {@link MetricsEndpointSource},
 * parses them with {@link OpenMetricsParser}, and delivers samples to the caller's consumer.
 *
 * <p>Errors fetching a single endpoint are logged and skipped so other endpoints
 * are still scraped.
 */
@Singleton
public class MetricsScraper {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsScraper.class);

    /** Fetches raw metrics text from a single URI. Extracted for testability. */
    @FunctionalInterface
    public interface MetricsFetcher {
        String fetch(URI uri) throws IOException;
    }

    private final MetricsEndpointSource source;
    private final OpenMetricsParser parser = new OpenMetricsParser();
    private final MetricsFetcher fetcher;

    /** Production constructor — uses a real {@link HttpClient}. */
    @jakarta.inject.Inject
    public MetricsScraper(MetricsEndpointSource source) {
        this(source, MetricsScraper.httpFetcher());
    }

    /** Test constructor — accepts a stub {@link MetricsFetcher}. */
    public MetricsScraper(MetricsEndpointSource source, MetricsFetcher fetcher) {
        this.source = source;
        this.fetcher = fetcher;
    }

    /**
     * Fetches metrics from all current endpoints and delivers parsed samples to {@code onSample}.
     * A fetch/parse error on one endpoint is logged as a warning; remaining endpoints are still scraped.
     */
    public void scrape(Consumer<OpenMetricsParser.Sample> onSample) {
        for (URI uri : source.endpoints()) {
            try {
                String text = fetcher.fetch(uri);
                parser.parse(text, onSample);
            } catch (IOException e) {
                LOG.warn("Failed to scrape metrics from {}: {}", uri, e.getMessage());
            }
        }
    }

    private static MetricsFetcher httpFetcher() {
        HttpClient http = HttpClient.newHttpClient();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + uri, e);
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri);
            }
            return response.body();
        };
    }
}
