package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import lombok.Getter;

public record ItemDataUpgrader(ItemIdMetaUpgrader idMetaUpgrader, LegacyItemIdToStringIdMap legacyIntToStringIdMap,
                               R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap, BlockItemIdMap blockItemIdMap) {
}
