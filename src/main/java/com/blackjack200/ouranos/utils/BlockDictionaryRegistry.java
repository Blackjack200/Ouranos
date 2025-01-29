package com.blackjack200.ouranos.utils;

import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
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
        val entry = RuntimeBlockMapping.getInstance(this.protocol);
        val hash = entry.fromRuntimeId(runtimeId);
        val states = entry.getBedrockKnownStates().get(hash);
        if (states == null) {
            return entry::getFallback;
        }
        return new SimpleBlockDefinition(states.getString("name"), runtimeId, states.getCompound("state"));
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        var id = RuntimeBlockMapping.getInstance(this.protocol).fromRuntimeId(blockDefinition.getRuntimeId());

        return id != null;
    }
}
