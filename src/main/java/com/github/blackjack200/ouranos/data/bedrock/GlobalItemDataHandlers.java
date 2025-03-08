package com.github.blackjack200.ouranos.data.bedrock;

import com.github.blackjack200.ouranos.data.LegacyItemIdToStringIdMap;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import com.github.blackjack200.ouranos.data.bedrock.item.downgrade.ItemIdMetaDowngrader;
import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.ItemDataUpgrader;
import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.ItemIdMetaUpgradeSchemaUtils;
import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.ItemIdMetaUpgrader;
import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.R12ItemIdToBlockIdMap;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalItemDataHandlers {
    private static ItemDataUpgrader itemDataUpgrader = null;
    private static Map<Integer, ItemIdMetaDowngrader> downgrader = new HashMap<>();


    @SneakyThrows
    public static ItemDataUpgrader getUpgrader() {
        if (itemDataUpgrader == null) {
            itemDataUpgrader = new ItemDataUpgrader(
                    new ItemIdMetaUpgrader(ItemIdMetaUpgradeSchemaUtils.loadSchemas("schema/id_meta_to_nbt", 1 << 30).values()),
                    LegacyItemIdToStringIdMap.getInstance(),
                    R12ItemIdToBlockIdMap.getInstance(),
                    BlockItemIdMap.getInstance()
            );

        }
        return itemDataUpgrader;
    }

    public static Map<Integer, Integer> SCHEMA_ID = new ConcurrentHashMap<>();

    public static int getSchemaId(int protocolId) {
        var id = SCHEMA_ID.getOrDefault(protocolId, null);
        if (id == null) {
            throw new RuntimeException("schemaid for protocol " + protocolId + " not found");
        }
        return id;
    }

    public static ItemIdMetaDowngrader getItemIdMetaDowngrader(int protocolId) {
        if (!downgrader.containsKey(protocolId)) {
            downgrader.put(protocolId, new ItemIdMetaDowngrader(ItemTypeDictionary.getInstance(protocolId), getSchemaId(protocolId)));
        }
        return downgrader.get(protocolId);
    }
}

