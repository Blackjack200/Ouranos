package com.blackjack200.ouranos.network.data.bedrock;

import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.*;

public final class GlobalItemDataHandlers {
    private static ItemDataUpgrader itemDataUpgrader = null;


    public static ItemDataUpgrader getUpgrader() {
        if (itemDataUpgrader == null) {
            itemDataUpgrader = new ItemDataUpgrader(
                    new ItemIdMetaUpgrader(ItemIdMetaUpgradeSchemaUtils.loadSchemas(Path.join(BEDROCK_ITEM_UPGRADE_SCHEMA_PATH, "id_meta_upgrade_schema"), Integer.MAX_VALUE)),
                    LegacyItemIdToStringIdMap.getInstance(),
                    R12ItemIdToBlockIdMap.getInstance(),
                    GlobalBlockStateHandlers.getUpgrader(),
                    BlockItemIdMap.getInstance()
            );
        }
        return itemDataUpgrader;
    }
}

