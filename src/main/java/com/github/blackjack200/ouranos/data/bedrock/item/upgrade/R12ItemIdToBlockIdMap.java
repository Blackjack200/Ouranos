package com.github.blackjack200.ouranos.data.bedrock.item.upgrade;

import com.github.blackjack200.ouranos.Ouranos;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.val;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps all known 1.12 and lower item IDs to their respective block IDs, where appropriate.
 * If an item ID does not have a corresponding 1.12 block ID, assume the item is not a blockitem.
 * <p>
 * This is only needed for deserializing blockitems from 1.8 and lower (or 1.12 and lower in the case of PM). In 1.9 and
 * above, the blockstate NBT is stored in the itemstack NBT, and the item ID is not used.
 */
public final class R12ItemIdToBlockIdMap {
    private static R12ItemIdToBlockIdMap instance;
    private final Map<String, String> itemToBlock = new HashMap<>();
    private final Map<String, String> blockToItem = new HashMap<>();

    private R12ItemIdToBlockIdMap(Map<String, String> itemToBlock) {
        for (Map.Entry<String, String> entry : itemToBlock.entrySet()) {
            String itemId = entry.getKey().toLowerCase();
            String blockId = entry.getValue().toLowerCase();
            this.itemToBlock.put(itemId, blockId);
            this.blockToItem.put(blockId, itemId);
        }
    }

    public static synchronized R12ItemIdToBlockIdMap getInstance() {
        if (instance == null) {
            instance = make();
        }
        return instance;
    }

    private static R12ItemIdToBlockIdMap make() {
        try {
            val schemaPath = Ouranos.class.getClassLoader().getResource("schema/block_legacy_id_map.json");
            if (schemaPath == null) {
                throw new RuntimeException("Unable to find schema/block_legacy_id_map.json");
            }
            Map<String, String> jsonObject = (new Gson()).fromJson(new InputStreamReader(schemaPath.openStream()), new TypeToken<Map<String, String>>() {
            }.getType());
            return new R12ItemIdToBlockIdMap(jsonObject);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read blockitem ID mapping table", e);
        }
    }

    public String itemIdToBlockId(String itemId) {
        return this.itemToBlock.get(itemId.toLowerCase());
    }

    public String blockIdToItemId(String blockId) {
        // Optional functionality for debugging
        return this.blockToItem.get(blockId.toLowerCase());
    }
}
