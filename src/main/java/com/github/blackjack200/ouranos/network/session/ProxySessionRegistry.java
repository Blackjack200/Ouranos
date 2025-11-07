package com.github.blackjack200.ouranos.network.session;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Central registry for all active proxy sessions. This replaces the previous
 * public static set to provide stronger encapsulation and future extension
 * points (metrics, rate limiting, etc.).
 */
public final class ProxySessionRegistry {
    private final ConcurrentMap<OuranosProxySession, Boolean> sessions =
        new ConcurrentHashMap<>();

    private static final ProxySessionRegistry INSTANCE = new ProxySessionRegistry();

    public static ProxySessionRegistry instance() {
        return INSTANCE;
    }

    private ProxySessionRegistry() {}

    public void register(OuranosProxySession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session, Boolean.TRUE);
    }

    public void unregister(OuranosProxySession session) {
        if (session == null) {
            return;
        }
        sessions.remove(session);
    }

    public int size() {
        return sessions.size();
    }

    public boolean contains(OuranosProxySession session) {
        return sessions.containsKey(session);
    }

    public Stream<OuranosProxySession> stream() {
        return sessions.keySet().stream();
    }

    public Collection<OuranosProxySession> snapshot() {
        return sessions.keySet();
    }

    public long countMatching(Predicate<OuranosProxySession> predicate) {
        return sessions.keySet().stream().filter(predicate).count();
    }
}
