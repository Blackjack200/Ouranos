package com.blackjack200.ouranos.data;

import lombok.Getter;

public class LegacyItemIdToStringIdMap extends LegacyToStringBidirectionalIdMap {
    @Getter
    private static final LegacyItemIdToStringIdMap instance;

    static {
        instance = new LegacyItemIdToStringIdMap();
    }

    public LegacyItemIdToStringIdMap() {
        super("item_id_map.json");
    }
}