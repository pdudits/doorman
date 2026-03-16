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
package io.zeromagic.doorman.cli;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Parses human-friendly duration strings (e.g. "5m", "15s", "1h30m", "50ms") into
 * {@link Duration} instances.
 *
 * <p>Supported format: {@code (\d+h)?(\d+m)?(\d+s)?(\d+ms)?} — at least one component required.
 * Note: "ms" must appear after "s" if both are present; prefer plain milliseconds ("50ms") for sub-second values.
 * ISO-8601 strings ({@code PT...}) are also accepted via {@link Duration#parse}.
 */
public final class DurationParser {

    private static final Pattern PATTERN =
            Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:(\\d+)s)?(?:(\\d+)ms)?");

    private DurationParser() {}

    /**
     * @throws IllegalArgumentException if the string is null, blank, or unrecognised
     */
    public static Duration parse(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Duration string must not be blank");
        }
        // ISO-8601 fast path
        if (s.startsWith("PT") || s.startsWith("pt") || s.startsWith("-PT")) {
            return Duration.parse(s.toUpperCase());
        }
        var matcher = PATTERN.matcher(s.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unrecognised duration format: '" + s + "'");
        }
        long hours   = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long minutes = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
        long seconds = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        long millis  = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

        if (hours == 0 && minutes == 0 && seconds == 0 && millis == 0) {
            throw new IllegalArgumentException("Unrecognised duration format: '" + s + "'");
        }
        return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusMillis(millis);
    }
}
