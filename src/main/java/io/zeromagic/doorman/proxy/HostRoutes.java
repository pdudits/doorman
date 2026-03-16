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
package io.zeromagic.doorman.proxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds the set of path-prefix → RouteTarget mappings for a single hostname.
 * The list is kept sorted in descending order by path-prefix length so that
 * {@link #resolve(String)} returns the longest match on the first hit.
 *
 * Thread-safe via a {@link ReentrantReadWriteLock}.
 */
class HostRoutes {

    private record RouteEntry(String pathPrefix, RouteTarget target) {}

    private static final Comparator<RouteEntry> BY_PREFIX_LENGTH_DESC =
            Comparator.comparingInt((RouteEntry e) -> e.pathPrefix().length()).reversed();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<RouteEntry> entries = new ArrayList<>();

    void add(String pathPrefix, RouteTarget target) {
        lock.writeLock().lock();
        try {
            entries.removeIf(e -> e.pathPrefix().equals(pathPrefix));
            entries.add(new RouteEntry(pathPrefix, target));
            entries.sort(BY_PREFIX_LENGTH_DESC);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void remove(String pathPrefix) {
        lock.writeLock().lock();
        try {
            entries.removeIf(e -> e.pathPrefix().equals(pathPrefix));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns the target for the longest path prefix that matches {@code path}. */
    Optional<RouteTarget> resolve(String path) {
        lock.readLock().lock();
        try {
            return entries.stream()
                    .filter(e -> path.startsWith(e.pathPrefix()))
                    .findFirst()
                    .map(RouteEntry::target);
        } finally {
            lock.readLock().unlock();
        }
    }

    boolean isEmpty() {
        lock.readLock().lock();
        try {
            return entries.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }
}
