package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import com.github.blackjack200.ouranos.network.session.Translate;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.common.util.Preconditions;

@Log4j2
@UtilityClass
public class TypeConverter {

    public SavedItemData fromNetworkId(int protocolId, int networkId, int networkMeta, Integer networkBlockRuntimeId) {
        var stringId = ItemTypeDictionary.getInstance(protocolId).fromIntId(networkId);
        var isBlockItem = BlockItemIdMap.getInstance().lookupItemId(protocolId, stringId) != null;
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
        Preconditions.checkArgument(input >= output, "Input version must be greater than output version");
        //downgrade item type
        ItemDefinition def = itemData.getDefinition();
        var data = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(def.getIdentifier(), itemData.getDamage());
        var i = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(data[0].toString(), (Integer) data[1]);
        String newStringId = i[0].toString();
        var newMeta = (Integer) i[1];
       // log.warn("translated {}:{} to {}:{}", itemData.getDefinition().getIdentifier(), itemData.getDamage(), newStringId, newMeta);

        var networkId = ItemTypeDictionary.getInstance(output).fromStringId(newStringId);
        if (networkId == null) {
            log.error("Unknown glk type {}", newStringId);
            return null;
        }

        var builder = itemData.toBuilder();
        builder.definition(new SimpleItemDefinition(newStringId, networkId, false))
                .damage(newMeta);
        //downgrade block runtime id
        var bid = BlockItemIdMap.getInstance().lookupItemId(output, newStringId);
        if (bid != null && !bid.equals(newStringId)) {
            log.error("Inconsistent item id map found for {}=>{}", newStringId, bid);
           // newStringId = bid;
        }
        if (BlockItemIdMap.getInstance().lookupItemId(output, newStringId) != null) {
            int trans = Translate.translateBlockRuntimeId(input, output, itemData.getBlockDefinition().getRuntimeId());

            builder.blockDefinition(new SimpleBlockDefinition(trans));
        }
        return builder.build();
    }
}
