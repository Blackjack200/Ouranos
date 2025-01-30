package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
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
            throw new RuntimeException("Item " + stringId + " is not a blockitem, but runtime ID $networkBlockRuntimeId was provided");
        }

        var d = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(stringId, networkMeta);
        stringId = (String) d[0];
        networkMeta = (Integer) d[1];
        return new SavedItemData(stringId, networkMeta, blockStateData, NbtMap.EMPTY);
    }

    public Integer[] toNetworkId(int inputProtocol, SavedItemData savedItemData) {
        var intId = ItemTypeDictionary.getInstance(inputProtocol).fromStringId(savedItemData.name());
        var blockStateData = savedItemData.block();
        Integer blockRuntimeId = null;
        if (blockStateData != null) {
            blockRuntimeId = BlockStateDictionary.getInstance(inputProtocol).toRuntimeId(blockStateData.stateHash());
            if (blockRuntimeId == null) {
                throw new RuntimeException("Unmapped blockstate returned by blockstate serializer: " + blockStateData);
            }
        }
        return new Integer[]{intId, savedItemData.meta(), blockRuntimeId};
    }

    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull()) {
            return itemData;
        }
        Integer blockRuntimeId = null;
        if (itemData.getBlockDefinition() != null) {
            blockRuntimeId = itemData.getBlockDefinition().getRuntimeId();
        }
        var savedItemData = fromNetworkId(input, itemData.getDefinition().getRuntimeId(), itemData.getDamage(), blockRuntimeId);

        var builder = itemData.toBuilder();
        var new_ = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(savedItemData.name(), savedItemData.meta());
        var newStringId = new_[0].toString();
        var newMeta = (Integer) new_[1];
        ItemTypeInfo typInfo = ItemTypeDictionary.getInstance(output).getEntries().get(newStringId);
        if (typInfo == null) {
            log.error("Unmapped blockstate returned by blockstate serializer: {}", savedItemData);
        }
        builder.definition(new SimpleItemDefinition(newStringId, typInfo.runtime_id(), typInfo.component_based()));
        builder.damage(newMeta);
        var block = savedItemData.block();
        if (block != null) {
            builder.blockDefinition(new SimpleBlockDefinition(BlockStateDictionary.getInstance(output).toRuntimeId(block.stateHash())));
        }
        return builder.build();
    }
}
