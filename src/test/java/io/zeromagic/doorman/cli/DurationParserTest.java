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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class DurationParserTest {

    @Test void seconds() {
        assertThat(DurationParser.parse("30s")).isEqualTo(Duration.ofSeconds(30));
    }

    @Test void minutes() {
        assertThat(DurationParser.parse("5m")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test void hours() {
        assertThat(DurationParser.parse("2h")).isEqualTo(Duration.ofHours(2));
    }

    @Test void minutesAndSeconds() {
        assertThat(DurationParser.parse("2m30s")).isEqualTo(Duration.ofMinutes(2).plusSeconds(30));
    }

    @Test void hoursMinutesSeconds() {
        assertThat(DurationParser.parse("1h5m10s"))
                .isEqualTo(Duration.ofHours(1).plusMinutes(5).plusSeconds(10));
    }

    @Test void iso8601() {
        assertThat(DurationParser.parse("PT5M")).isEqualTo(Duration.ofMinutes(5));
        assertThat(DurationParser.parse("PT1H30M")).isEqualTo(Duration.ofHours(1).plusMinutes(30));
    }

    @Test void milliseconds() {
        assertThat(DurationParser.parse("50ms")).isEqualTo(Duration.ofMillis(50));
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("1000ms")).isEqualTo(Duration.ofMillis(1000));
    }

    @Test void secondsAndMilliseconds() {
        assertThat(DurationParser.parse("1s500ms")).isEqualTo(Duration.ofSeconds(1).plusMillis(500));
    }

    @Test void blankThrows() {
        assertThatThrownBy(() -> DurationParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unrecognisedThrows() {
        assertThatThrownBy(() -> DurationParser.parse("fast"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void zeroStringThrows() {
        assertThatThrownBy(() -> DurationParser.parse("0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
