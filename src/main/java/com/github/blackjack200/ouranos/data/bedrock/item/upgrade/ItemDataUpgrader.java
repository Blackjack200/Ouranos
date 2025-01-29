package com.github.blackjack200.ouranos.data.bedrock.item.upgrade;

import com.github.blackjack200.ouranos.data.LegacyItemIdToStringIdMap;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;

public record ItemDataUpgrader(ItemIdMetaUpgrader idMetaUpgrader, LegacyItemIdToStringIdMap legacyIntToStringIdMap,
                               R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap, BlockItemIdMap blockItemIdMap) {
}
