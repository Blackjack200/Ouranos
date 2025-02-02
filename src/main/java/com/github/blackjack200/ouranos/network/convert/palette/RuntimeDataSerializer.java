package com.github.blackjack200.ouranos.network.convert.palette;

/**
 * @author JukeboxMC | daoge_cmd
 */
@FunctionalInterface
public interface RuntimeDataSerializer<V> {
    int serialize(V value);
}
