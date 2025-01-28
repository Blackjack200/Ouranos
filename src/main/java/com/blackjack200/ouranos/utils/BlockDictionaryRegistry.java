package com.blackjack200.ouranos.utils;

import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class BlockDictionaryRegistry implements DefinitionRegistry<BlockDefinition> {
    public final int destination;

    public BlockDictionaryRegistry(int destination) {
        this.destination = destination;
    }

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        Integer hash = RuntimeBlockMapping.getInstance(this.destination).fromRuntimeId(runtimeId);
        NbtMap states = RuntimeBlockMapping.getInstance(this.destination).getBedrockKnownStates().get(hash);
        if (states == null) {
            return () -> RuntimeBlockMapping.getInstance(this.destination).getFallback();
        }
        return new SimpleBlockDefinition(states.getString("name"), runtimeId, states.getCompound("state"));
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        var id = RuntimeBlockMapping.getInstance(this.destination).fromRuntimeId(blockDefinition.getRuntimeId());

        return id != null;
    }

    record UnknownDefinition(int runtimeId) implements BlockDefinition {

        @Override
        public int getRuntimeId() {
            return runtimeId;
        }
    }
}
