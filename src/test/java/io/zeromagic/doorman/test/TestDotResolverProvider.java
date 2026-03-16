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
package io.zeromagic.doorman.test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

/**
 * Resolves any hostname ending in {@code .test} to the loopback address (127.0.0.1 / ::1).
 *
 * <p>Registered as a JEP-418 {@link InetAddressResolverProvider} via the SPI file
 * {@code META-INF/services/java.net.spi.InetAddressResolverProvider} in test resources.
 * This lets tests use real hostnames like {@code my-service.test} with {@code HttpClient}
 * or {@code HttpURLConnection} — the {@code Host} header is set automatically by the JDK,
 * and no manual header manipulation is needed.
 *
 * <p>Used in {@code ProxyServerTest} and future system / integration tests.
 */
public class TestDotResolverProvider extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new TestDotResolver(configuration.builtinResolver());
    }

    @Override
    public String name() {
        return "Doorman .test resolver";
    }

    private static final class TestDotResolver implements InetAddressResolver {

        private final InetAddressResolver builtin;

        TestDotResolver(InetAddressResolver builtin) {
            this.builtin = builtin;
        }

        @Override
        public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
            if (host.endsWith(".test")) {
                return Stream.of(InetAddress.getLoopbackAddress());
            }
            return builtin.lookupByName(host, policy);
        }

        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            return builtin.lookupByAddress(addr);
        }
    }
}
