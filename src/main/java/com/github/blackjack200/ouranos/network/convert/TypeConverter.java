package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import com.github.blackjack200.ouranos.network.session.Translate;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.common.util.Preconditions;

@Log4j2
@UtilityClass
public class TypeConverter {
    public ItemData translateItemData(int input, int output, ItemData itemData) {
        Preconditions.checkArgument(input >= output, "Input version must be greater than output version");
        if (itemData.isNull() || !itemData.isValid()) {
            return itemData;
        }

        //downgrade item type
        var def = itemData.getDefinition();
        var data = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(def.getIdentifier(), itemData.getDamage());
        var i = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(data[0].toString(), (Integer) data[1]);

        String newStringId = i[0].toString();
        var newMeta = (Integer) i[1];

        var networkId = ItemTypeDictionary.getInstance(output).fromStringId(newStringId);
        if (networkId == null) {
            //log.error("Unknown glk type {}", newStringId);
            return null;
        }

        var builder = itemData.toBuilder();
        builder.definition(new SimpleItemDefinition(newStringId, networkId, false))
                .damage(newMeta);

        //downgrade block runtime id
        var bid = BlockItemIdMap.getInstance().lookupItemId(output, newStringId);
        if (bid != null && !bid.equals(newStringId)) {
            log.debug("Inconsistent item id map found for {}=>{}", newStringId, bid);
            newStringId = bid;
        }

        if (BlockItemIdMap.getInstance().lookupItemId(output, newStringId) != null && itemData.getBlockDefinition() != null) {
            int trans = Translate.translateBlockRuntimeId(input, output, itemData.getBlockDefinition().getRuntimeId());
            builder.blockDefinition(new SimpleBlockDefinition(trans));
        }

        return builder.build();
    }
}
