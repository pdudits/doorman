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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMetricsParserTest {

    private final OpenMetricsParser parser = new OpenMetricsParser();

    private List<OpenMetricsParser.Sample> parseAll(String text) {
        var samples = new ArrayList<OpenMetricsParser.Sample>();
        parser.parse(text, samples::add);
        return samples;
    }

    // ── basic parsing ────────────────────────────────────────────────────────

    @Test
    void parse_singleSampleWithLabels() {
        String text = """
                traefik_service_requests_total{code="200",service="default-myapp-80@kubernetes"} 42
                """;

        var samples = parseAll(text);

        assertThat(samples).hasSize(1);
        var s = samples.getFirst();
        assertThat(s.metricName()).isEqualTo("traefik_service_requests_total");
        assertThat(s.labels()).containsEntry("code", "200")
                              .containsEntry("service", "default-myapp-80@kubernetes");
        assertThat(s.value()).isEqualTo(42.0);
    }

    @Test
    void parse_sampleWithoutLabels() {
        var samples = parseAll("go_goroutines 42");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().metricName()).isEqualTo("go_goroutines");
        assertThat(samples.getFirst().labels()).isEmpty();
        assertThat(samples.getFirst().value()).isEqualTo(42.0);
    }

    @Test
    void parse_floatValue() {
        var samples = parseAll("some_metric 3.14");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().value()).isEqualTo(3.14);
    }

    @Test
    void parse_valueWithTimestamp_parsesValue() {
        var samples = parseAll("some_metric 99 1609459200000");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().value()).isEqualTo(99.0);
    }

    // ── filtering ────────────────────────────────────────────────────────────

    @Test
    void parse_callerCanFilterByLabelValue() {
        String text = """
                traefik_service_requests_total{code="200",service="ns-svc-80@kubernetes"} 10
                traefik_service_requests_total{code="404",service="ns-svc-80@kubernetes"} 5
                traefik_service_requests_total{code="200",service="ns-other-80@kubernetes"} 100
                """;

        double[] total = {0};
        parser.parse(text, sample -> {
            if ("traefik_service_requests_total".equals(sample.metricName())
                    && "ns-svc-80@kubernetes".equals(sample.labels().get("service"))) {
                total[0] += sample.value();
            }
        });

        assertThat(total[0]).isEqualTo(15.0);
    }

    @Test
    void parse_multipleSamplesForSameMetricDifferentLabels() {
        String text = """
                http_requests_total{method="GET"} 100
                http_requests_total{method="POST"} 50
                """;

        var samples = parseAll(text);

        assertThat(samples).hasSize(2);
        assertThat(samples.stream().mapToDouble(OpenMetricsParser.Sample::value).sum()).isEqualTo(150.0);
    }

    // ── skipping ─────────────────────────────────────────────────────────────

    @Test
    void parse_commentLinesSkipped() {
        String text = """
                # HELP traefik_service_requests_total Total number of requests
                # TYPE traefik_service_requests_total counter
                traefik_service_requests_total{service="svc"} 7
                """;

        var samples = parseAll(text);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().value()).isEqualTo(7.0);
    }

    @Test
    void parse_blankLinesSkipped() {
        String text = "\n\nsome_metric 1\n\n";

        assertThat(parseAll(text)).hasSize(1);
    }

    @Test
    void parse_emptyInput_returnsNoSamples() {
        assertThat(parseAll("")).isEmpty();
        assertThat(parseAll("   ")).isEmpty();
    }

    @Test
    void parse_nullInput_returnsNoSamples() {
        assertThat(parseAll(null)).isEmpty();
    }

    @Test
    void parse_malformedValue_lineSkipped() {
        String text = """
                valid_metric 10
                bad_metric{label="x"} not-a-number
                another_valid 20
                """;

        var samples = parseAll(text);

        assertThat(samples).hasSize(2);
        assertThat(samples.stream().mapToDouble(OpenMetricsParser.Sample::value).sum()).isEqualTo(30.0);
    }

    @Test
    void parse_unclosedBrace_lineSkipped() {
        String text = """
                valid_metric 5
                broken_metric{label="x" 99
                """;

        assertThat(parseAll(text)).hasSize(1);
    }

    // ── label edge cases ─────────────────────────────────────────────────────

    @Test
    void parse_escapedQuoteInLabelValue() {
        var samples = parseAll("metric{label=\"val\\\"ue\"} 1");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().labels()).containsEntry("label", "val\"ue");
    }

    @Test
    void parse_emptyLabelSet() {
        var samples = parseAll("metric{} 42");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().labels()).isEmpty();
    }
}
