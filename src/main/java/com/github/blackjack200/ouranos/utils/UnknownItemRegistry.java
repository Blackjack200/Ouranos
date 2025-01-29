package com.github.blackjack200.ouranos.utils;

import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.common.Definition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class UnknownItemRegistry<T extends Definition> implements DefinitionRegistry<T> {
    @Override
    public T getDefinition(int runtimeId) {
        return (T) new UnknownDefinition(runtimeId);
    }

    @Override
    public boolean isRegistered(T definition) {
        return true;
    }

     record UnknownDefinition(int runtimeId) implements Definition, ItemDefinition {
        @Override
        public int getRuntimeId() {
            return runtimeId;
        }

        @Override
        public boolean isComponentBased() {
            return true;
        }

        @Override
        public String getIdentifier() {
            return "minecraft:unknown";
        }
    }
}
