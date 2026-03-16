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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stateless parser for the OpenMetrics / Prometheus text exposition format.
 * Uses an event/consumer style: the caller registers a handler that is called
 * for each parsed sample. This avoids building an in-memory map of all metrics
 * when only a small subset is of interest.
 *
 * <p>Example usage:
 * <pre>
 *   double[] total = {0};
 *   parser.parse(text, sample -> {
 *       if ("traefik_service_requests_total".equals(sample.metricName())
 *               && "my-svc".equals(sample.labels().get("service"))) {
 *           total[0] += sample.value();
 *       }
 *   });
 * </pre>
 */
public class OpenMetricsParser {

    /** A single metric data point emitted by the parser. */
    public record Sample(String metricName, Map<String, String> labels, double value) {}

    /**
     * Parses {@code text} in OpenMetrics/Prometheus text format and calls
     * {@code onSample} for every successfully parsed data line.
     * Comment lines ({@code #}), blank lines, and malformed lines are skipped silently.
     */
    public void parse(String text, Consumer<Sample> onSample) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            parseLine(trimmed, onSample);
        }
    }

    private void parseLine(String line, Consumer<Sample> onSample) {
        try {
            int braceOpen = line.indexOf('{');
            if (braceOpen >= 0) {
                // metric_name{labels} value [timestamp]
                String metricName = line.substring(0, braceOpen).trim();
                int braceClose = line.indexOf('}', braceOpen);
                if (braceClose < 0) return;
                String labelStr = line.substring(braceOpen + 1, braceClose);
                String remainder = line.substring(braceClose + 1).trim();
                double value = parseValue(remainder);
                onSample.accept(new Sample(metricName, parseLabels(labelStr), value));
            } else {
                // metric_name value [timestamp]  (no labels)
                int space = line.indexOf(' ');
                if (space < 0) return;
                String metricName = line.substring(0, space).trim();
                double value = parseValue(line.substring(space + 1).trim());
                onSample.accept(new Sample(metricName, Map.of(), value));
            }
        } catch (Exception ignored) {
            // malformed line — skip silently
        }
    }

    /** Parses the first whitespace-delimited token as a double (ignores optional timestamp). */
    private double parseValue(String remainder) {
        int space = remainder.indexOf(' ');
        String token = space >= 0 ? remainder.substring(0, space) : remainder;
        return Double.parseDouble(token);
    }

    /**
     * Parses a label string of the form {@code key="value",key2="value2"} into a Map.
     * Values may contain escaped characters; only {@code \"} unescaping is applied.
     */
    private Map<String, String> parseLabels(String labelStr) {
        Map<String, String> labels = new HashMap<>();
        if (labelStr.isBlank()) return labels;
        // Split on comma boundaries that are outside quoted strings
        int i = 0;
        while (i < labelStr.length()) {
            int eq = labelStr.indexOf('=', i);
            if (eq < 0) break;
            String key = labelStr.substring(i, eq).trim();
            if (eq + 1 >= labelStr.length() || labelStr.charAt(eq + 1) != '"') break;
            // find closing quote, respecting escapes
            int valStart = eq + 2;
            int valEnd = valStart;
            while (valEnd < labelStr.length()) {
                char c = labelStr.charAt(valEnd);
                if (c == '\\') { valEnd += 2; continue; }
                if (c == '"') break;
                valEnd++;
            }
            String value = labelStr.substring(valStart, valEnd).replace("\\\"", "\"");
            labels.put(key, value);
            i = valEnd + 2; // skip closing quote and comma
        }
        return labels;
    }
}
