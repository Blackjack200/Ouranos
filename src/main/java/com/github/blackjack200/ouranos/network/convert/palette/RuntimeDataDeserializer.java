package com.github.blackjack200.ouranos.network.convert.palette;

/**
 * @author JukeboxMC | daoge_cmd
 */
@FunctionalInterface
public interface RuntimeDataDeserializer<V> {
    V deserialize(int id);
}
