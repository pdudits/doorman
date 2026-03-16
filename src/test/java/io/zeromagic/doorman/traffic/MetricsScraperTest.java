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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsScraperTest {

    private static final URI URI1 = URI.create("http://host1:9100/metrics");
    private static final URI URI2 = URI.create("http://host2:9100/metrics");

    @Test
    void scrape_mergesSamplesFromMultipleEndpoints() {
        MetricsEndpointSource source = () -> List.of(URI1, URI2);
        MetricsScraper.MetricsFetcher fetcher = uri -> switch (uri.getHost()) {
            case "host1" -> "requests_total 10\n";
            case "host2" -> "requests_total 20\n";
            default -> throw new IOException("unexpected: " + uri);
        };

        var samples = new ArrayList<OpenMetricsParser.Sample>();
        new MetricsScraper(source, fetcher).scrape(samples::add);

        assertThat(samples).hasSize(2);
        assertThat(samples.stream().mapToDouble(OpenMetricsParser.Sample::value).sum()).isEqualTo(30.0);
    }

    @Test
    void scrape_skipsFailingEndpointAndContinues() {
        MetricsEndpointSource source = () -> List.of(URI1, URI2);
        MetricsScraper.MetricsFetcher fetcher = uri -> {
            if (uri.equals(URI1)) return "requests_total 42\n";
            throw new IOException("connection refused");
        };

        var samples = new ArrayList<OpenMetricsParser.Sample>();
        new MetricsScraper(source, fetcher).scrape(samples::add);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).value()).isEqualTo(42.0);
    }

    @Test
    void scrape_emptySourceProducesNoSamples() {
        MetricsEndpointSource source = List::of;
        var samples = new ArrayList<OpenMetricsParser.Sample>();
        new MetricsScraper(source, uri -> { throw new IOException("should not be called"); })
                .scrape(samples::add);
        assertThat(samples).isEmpty();
    }
}
