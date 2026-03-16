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
package io.zeromagic.doorman;

import io.avaje.inject.BeanScope;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.zeromagic.doorman.cli.CliArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 *
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        var cliArgs = new CliArgs();
        var commandLine = new CommandLine(cliArgs);
        var parseResult = commandLine.parseArgs(args);

        if (parseResult.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            return;
        }

        if (parseResult.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            return;
        }

        try {
            var scope = BeanScope.builder().bean(CliArgs.class, cliArgs)
                    .shutdownHook(true)
                    .build();

            var client = scope.get(KubernetesClient.class);
            System.out.println(client.getMasterUrl());
            System.out.println(client.getKubernetesVersion().getMajor() + "." + client.getKubernetesVersion().getMinor());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
