package com.github.blackjack200.ouranos.utils;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;

@Value
@NonFinal
@AllArgsConstructor
public class SimpleVersionedItemDefinition implements ItemDefinition {
    String identifier;
    int runtimeId;
    ItemVersion version;
    boolean componentBased;
    NbtMap componentData;
}
