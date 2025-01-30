package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

@Log4j2
@UtilityClass
public class TypeConverter {

    public SavedItemData fromNetworkId(int protocolId, int networkId, int networkMeta, Integer networkBlockRuntimeId) {
        var stringId = ItemTypeDictionary.getInstance(protocolId).fromIntId(networkId);
        var isBlockItem = BlockItemIdMap.getInstance().lookupBlockId(protocolId, stringId) != null;
        BlockStateDictionary.Dictionary.BlockEntry blockStateData = null;
        if (isBlockItem) {
            var mapping = BlockStateDictionary.getInstance(protocolId);
            blockStateData = mapping.lookupStateFromStateHash(mapping.toStateHash(networkBlockRuntimeId));
        } else if (networkBlockRuntimeId != null) {
            //throw new RuntimeException("Item " + stringId + " is not a blockitem, but runtime ID " + networkBlockRuntimeId + " was provided");
        }
        var d = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(stringId, networkMeta);
        stringId = (String) d[0];
        networkMeta = (Integer) d[1];
        return new SavedItemData(stringId, networkMeta, blockStateData, NbtMap.EMPTY);
    }

    public Integer[] toNetworkId(int protocol, SavedItemData savedItemData) {
        var new_ = GlobalItemDataHandlers.getItemIdMetaDowngrader(protocol).downgrade(savedItemData.name(), savedItemData.meta());

        var newStringId = new_[0].toString();
        var newMeta = (Integer) new_[1];

        log.warn("downgrade {}:{} to {}:{}", savedItemData.name(), savedItemData.meta(), new_[0], new_[1]);

        var intId = ItemTypeDictionary.getInstance(protocol).fromStringId(newStringId);

        if (intId == null) {
            log.error("Unknown item type {}", newStringId);
            val barrierNamespaceId = "minecraft:info_update";
            val barrier = ItemData.builder()
                    .definition(new SimpleItemDefinition(barrierNamespaceId, ItemTypeDictionary.getInstance(protocol).fromStringId(barrierNamespaceId), false))
                    .count(1)
                    .blockDefinition(() -> BlockStateDictionary.getInstance(protocol).getFallback())
                    .build();
            return new Integer[]{barrier.getDefinition().getRuntimeId(), barrier.getDamage(), barrier.getBlockDefinition().getRuntimeId()};
        }

        var blockStateData = savedItemData.block();
        Integer blockRuntimeId = null;
        if (blockStateData != null) {
            blockRuntimeId = BlockStateDictionary.getInstance(protocol).toRuntimeId(blockStateData.stateHash());
            if (blockRuntimeId == null) {
                log.error("Unmapped blockstate returned by blockstate serializer: {}", blockStateData);
                blockRuntimeId = BlockStateDictionary.getInstance(protocol).getFallback();
            }
        }
        return new Integer[]{intId, newMeta, blockRuntimeId};
    }

    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull()) {
            return itemData;
        }
        Integer blockRuntimeId = null;
        if (itemData.getBlockDefinition() != null) {
            blockRuntimeId = itemData.getBlockDefinition().getRuntimeId();
        }
        var builder = itemData.toBuilder();

        var savedItemData = fromNetworkId(input, itemData.getDefinition().getRuntimeId(), itemData.getDamage(), blockRuntimeId);
        var converted = toNetworkId(output, savedItemData);
        var newIntId = converted[0];
        var newMeta = converted[1];
        var newBlockRuntimeId = converted[2];
        builder.definition(new SimpleItemDefinition(ItemTypeDictionary.getInstance(output).fromIntId(newIntId), newIntId, false))
                .damage(newMeta);
        if (newBlockRuntimeId != null) {
            builder.blockDefinition(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition(savedItemData.name(), newBlockRuntimeId, savedItemData.block().stateData()));
        }
        //   log.warn("Translating item data from {} to {}", savedItemData, builder.build());
        return builder.build();
    }
}
