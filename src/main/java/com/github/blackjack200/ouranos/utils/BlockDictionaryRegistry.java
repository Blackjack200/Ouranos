package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class BlockDictionaryRegistry implements DefinitionRegistry<BlockDefinition> {
    public final int protocol;
    public final boolean hashed;

    public BlockDictionaryRegistry(int protocol, boolean hashed) {
        this.protocol = protocol;
        this.hashed = hashed;
    }

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        val entry = BlockStateDictionary.getInstance(this.protocol);
        Integer hash;
        if (this.hashed) {
            hash = runtimeId;
        } else {
            hash = entry.toLatestStateHash(runtimeId);
        }
        val states = entry.lookupStateFromStateHash(hash);
        if (states == null) {
            return null;
        }
        return new SimpleBlockDefinition(states.name(), runtimeId, states.rawState());
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        return true;
    }
}
