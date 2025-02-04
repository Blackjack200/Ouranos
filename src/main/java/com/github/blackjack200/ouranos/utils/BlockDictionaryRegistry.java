package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class BlockDictionaryRegistry implements DefinitionRegistry<BlockDefinition> {
    public final int protocol;

    public BlockDictionaryRegistry(int protocol) {
        this.protocol = protocol;
    }

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        val entry = BlockStateDictionary.getInstance(this.protocol);
        val hash = entry.toLatestStateHash(runtimeId);
        val states = entry.lookupStateFromStateHash(hash);
        if (states == null) {
            return entry::getFallbackRuntimeId;
        }
        return new SimpleBlockDefinition(states.name(), runtimeId, states.rawState().getCompound("states"));
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        var id = BlockStateDictionary.getInstance(this.protocol).toLatestStateHash(blockDefinition.getRuntimeId());
        return id != null;
    }
}
