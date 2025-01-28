package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import com.blackjack200.ouranos.network.data.LegacyToStringIdMap;

import java.nio.file.Path;

public final class LegacyBlockIdToStringIdMap extends LegacyToStringIdMap {

    private static LegacyBlockIdToStringIdMap instance;

    private LegacyBlockIdToStringIdMap() {
        super(Path.of(BEDROCK_BLOCK_UPGRADE_SCHEMA_PATH, "block_legacy_id_map.json"));
    }

    public static LegacyBlockIdToStringIdMap getInstance() {
        if (instance == null) {
            instance = new LegacyBlockIdToStringIdMap();
        }
        return instance;
    }
}

