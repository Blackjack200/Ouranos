package com.blackjack200.ouranos.network.convert;

public class LegacyItemIdToStringIdMap extends LegacyToStringBidirectionalIdMap {
    private static final LegacyItemIdToStringIdMap instance;

    static {
        instance = new LegacyItemIdToStringIdMap();
    }

    public static LegacyItemIdToStringIdMap getInstance() {
        return instance;
    }

    public LegacyItemIdToStringIdMap() {
        super("item_id_map.json");
    }
}
