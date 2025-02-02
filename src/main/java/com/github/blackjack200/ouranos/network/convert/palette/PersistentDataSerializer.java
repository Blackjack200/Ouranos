package com.github.blackjack200.ouranos.network.convert.palette;

import org.cloudburstmc.nbt.NbtMap;

/**
 * @author JukeboxMC | daoge_cmd
 */
@FunctionalInterface
public interface PersistentDataSerializer<V> {
    NbtMap serialize(V value);
}
