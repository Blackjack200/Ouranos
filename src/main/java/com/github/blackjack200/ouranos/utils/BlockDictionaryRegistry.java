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
        val hash = entry.toStateHash(runtimeId);
        val states = entry.lookupStateFromStateHash(hash);
        if (states == null) {
            return entry::getFallback;
        }
        return new SimpleBlockDefinition(states.name(), runtimeId, states.stateData().getCompound("states"));
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        var id = BlockStateDictionary.getInstance(this.protocol).toStateHash(blockDefinition.getRuntimeId());
        return id != null;
    }
}
