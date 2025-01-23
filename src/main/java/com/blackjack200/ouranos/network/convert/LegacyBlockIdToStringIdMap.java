package com.blackjack200.ouranos.network.convert;

public class LegacyBlockIdToStringIdMap extends LegacyToStringBidirectionalIdMap {
    private static LegacyBlockIdToStringIdMap instance;

    static {
        instance = new LegacyBlockIdToStringIdMap();
    }

    public static LegacyBlockIdToStringIdMap getInstance() {
        return instance;
    }

    public LegacyBlockIdToStringIdMap() {
        super("block_id_map.json");
    }
}