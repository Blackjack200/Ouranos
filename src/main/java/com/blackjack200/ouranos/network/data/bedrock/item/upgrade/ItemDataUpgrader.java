package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import lombok.Getter;

@Getter
public class ItemDataUpgrader {
    private final ItemIdMetaUpgrader idMetaUpgrader;
    private final LegacyItemIdToStringIdMap legacyIntToStringIdMap;
    private final R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap;
    private final BlockItemIdMap blockItemIdMap;

    public ItemDataUpgrader(ItemIdMetaUpgrader idMetaUpgrader,
                            LegacyItemIdToStringIdMap legacyIntToStringIdMap,
                            R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap,
                            BlockItemIdMap blockItemIdMap) {
        this.idMetaUpgrader = idMetaUpgrader;
        this.legacyIntToStringIdMap = legacyIntToStringIdMap;
        this.r12ItemIdToBlockIdMap = r12ItemIdToBlockIdMap;
        this.blockItemIdMap = blockItemIdMap;
    }
}
