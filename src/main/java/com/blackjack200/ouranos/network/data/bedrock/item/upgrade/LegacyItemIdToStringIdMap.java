package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import com.blackjack200.ouranos.Ouranos;
import com.blackjack200.ouranos.network.data.LegacyToStringIdMap;

/**
 * A class that maps legacy item IDs to string IDs using a predefined JSON schema.
 * This class follows the Singleton pattern.
 */
public final class LegacyItemIdToStringIdMap extends LegacyToStringIdMap {

    private static LegacyItemIdToStringIdMap instance;

    private LegacyItemIdToStringIdMap() {
        super(Ouranos.class.getClassLoader().getResource("upgrade_item/1.12.0_item_id_to_block_id_map.json"));
    }

    /**
     * Singleton method to get the single instance of this class.
     *
     * @return the single instance of LegacyItemIdToStringIdMap
     */
    public static synchronized LegacyItemIdToStringIdMap getInstance() {
        if (instance == null) {
            instance = new LegacyItemIdToStringIdMap();
        }
        return instance;
    }
}

