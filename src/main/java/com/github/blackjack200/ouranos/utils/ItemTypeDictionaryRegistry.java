package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

@Log4j2
public class ItemTypeDictionaryRegistry implements DefinitionRegistry<ItemDefinition> {
    private final ItemTypeDictionary.InnerEntry protocol;

    public ItemTypeDictionaryRegistry(ItemTypeDictionary.InnerEntry protocol) {
        this.protocol = protocol;
    }

    @Override
    public ItemDefinition getDefinition(int runtimeId) {
        var dict = this.protocol;
        var strId = dict.fromIntId(runtimeId);
        var x = dict.getEntries().get(strId);
        return new SimpleVersionedItemDefinition(strId, x.getRuntimeId(), x.getVersion(), x.isComponentBased(), x.getComponentData());
    }

    @Override
    public boolean isRegistered(ItemDefinition definition) {
        return this.protocol.fromStringId(definition.getIdentifier()) != null;
    }
}
