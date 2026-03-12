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

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;
import picocli.CommandLine;

import java.util.Optional;

@Factory
class ConfigProvider {
    private final CliArgs args;

    protected ConfigProvider(@External RawArgs args) {
        var cmdline = new CommandLine(new CliArgs()).parseArgs(args.args()).asCommandLineList();
        this.args = cmdline.getFirst().getCommand();
    }

    @Bean
    Optional<KubernetesConfig> kubernetesConfig() {
        return Optional.ofNullable(args.kubeContext).map(KubernetesConfig::new);
    }
}
