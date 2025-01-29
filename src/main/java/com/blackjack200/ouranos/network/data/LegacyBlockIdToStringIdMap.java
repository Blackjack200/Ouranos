package com.blackjack200.ouranos.network.data;

import lombok.Getter;

public class LegacyBlockIdToStringIdMap extends LegacyToStringBidirectionalIdMap {
    @Getter
    private static final LegacyBlockIdToStringIdMap instance;

    static {
        instance = new LegacyBlockIdToStringIdMap();
    }

    public LegacyBlockIdToStringIdMap() {
        super("block_id_map.json");
    }
}