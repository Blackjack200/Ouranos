package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates per-translation state so individual stages receive a single handle
 * with all context they might need, similar to how Rust code often threads a
 * shared struct across systems.
 */
public record TranslationContext(
    int inputProtocol,
    int outputProtocol,
    TranslationDirection direction,
    OuranosProxySession session,
    Map<String, Object> attributes
) {
    public TranslationContext {
        attributes = attributes == null
            ? new ConcurrentHashMap<>()
            : attributes;
    }

    public boolean protocolsAreEqual() {
        return inputProtocol == outputProtocol;
    }

    public boolean fromServer() {
        return direction.isClientbound();
    }

    public boolean toServer() {
        return direction.isServerbound();
    }

    public <T> void putAttribute(String key, T value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public <T> Optional<T> getAttribute(String key, Class<T> type) {
        var value = attributes.get(key);
        if (value == null || !type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }
}
