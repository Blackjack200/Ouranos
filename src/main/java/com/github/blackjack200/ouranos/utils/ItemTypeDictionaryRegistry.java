package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class ItemTypeDictionaryRegistry implements DefinitionRegistry<ItemDefinition> {
    private final int protocol;

    public ItemTypeDictionaryRegistry(int protocol) {
        this.protocol = protocol;
    }

    @Override
    public ItemDefinition getDefinition(int runtimeId) {
        var x = ItemTypeDictionary.getInstance(protocol).fromIntId(runtimeId);
        return new SimpleItemDefinition(x, runtimeId, false);
    }

    @Override
    public boolean isRegistered(ItemDefinition definition) {
        return ItemTypeDictionary.getInstance(protocol).fromStringId(definition.getIdentifier()) != null;
    }
}
