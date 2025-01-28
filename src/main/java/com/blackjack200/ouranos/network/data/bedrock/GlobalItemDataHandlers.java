package com.blackjack200.ouranos.network.data.bedrock;

import com.blackjack200.ouranos.Ouranos;
import com.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.downgrade.ItemIdMetaDowngrader;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.ItemDataUpgrader;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.ItemIdMetaUpgradeSchemaUtils;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.ItemIdMetaUpgrader;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.R12ItemIdToBlockIdMap;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

public final class GlobalItemDataHandlers {
    private static ItemDataUpgrader itemDataUpgrader = null;
    private static Map<Integer, ItemIdMetaDowngrader> downgrader = new HashMap<>();


    @SneakyThrows
    public static ItemDataUpgrader getUpgrader() {
        if (itemDataUpgrader == null) {
            itemDataUpgrader = new ItemDataUpgrader(
                    new ItemIdMetaUpgrader(ItemIdMetaUpgradeSchemaUtils.loadSchemas(Ouranos.class.getClassLoader().getResource("upgrade_item/id_meta_upgrade_schema").getPath(), Integer.MAX_VALUE).values()),
                    LegacyItemIdToStringIdMap.getInstance(),
                    R12ItemIdToBlockIdMap.getInstance(),
                    BlockItemIdMap.getInstance()
            );
        }
        return itemDataUpgrader;
    }

    public static int getSchemaId(int protocolId) {
        return switch (protocolId) {
            case 766 -> 231;
            case 748 -> 221;
            case 729 -> 211;
            case 712 -> 201;
            case 767 -> 686;
            case 685 -> 191;
            case 671 -> 181;
            case 662 -> 171;
            case 649 -> 161;
            case 630 -> 151;
            case 768 -> 622;
            case 618 -> 141;
            case 594 -> 121;
            case 589 -> 111;
            default -> throw new RuntimeException("schemaid for protocol " + protocolId + " not found");
        };
    }

    public static ItemIdMetaDowngrader getItemIdMetaDowngrader(int protocolId) {
        if (!downgrader.containsKey(protocolId)) {
            downgrader.put(protocolId, new ItemIdMetaDowngrader(ItemTypeDictionary.getInstance(), protocolId, getSchemaId(protocolId)));
        }
        return downgrader.get(protocolId);
    }
}

