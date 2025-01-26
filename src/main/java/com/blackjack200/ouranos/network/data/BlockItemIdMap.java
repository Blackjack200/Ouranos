package com.blackjack200.ouranos.network.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class BlockItemIdMap extends AbstractMapping {
    private static final BlockItemIdMap instance;

    static {
        instance = new BlockItemIdMap();
    }

    public static BlockItemIdMap getInstance() {
        return instance;
    }

    private Map<Integer, Map<String, String>> blockToItemId = new HashMap<>();
    private Map<Integer, Map<String, String>> itemToBlockId = new HashMap<>();

    public static <K, V> Map<V, K> reverseMap(Map<K, V> originalMap) {
        Map<V, K> reversedMap = new HashMap<>();
        for (Map.Entry<K, V> entry : originalMap.entrySet()) {
            reversedMap.put(entry.getValue(), entry.getKey());
        }
        return reversedMap;
    }

    private BlockItemIdMap() {
        load("block_id_to_item_id_map.json", (protocol, rawData) -> {
            var map = new Gson().fromJson(new String(rawData), new TypeToken<Map<String, String>>() {
            });
            this.blockToItemId.put(protocol, map);
            this.itemToBlockId.put(protocol, reverseMap(map));
        });
    }

    public String lookupItemId(int protocolId, String blockId) {
        return blockToItemId.get(protocolId).getOrDefault(blockId, null);
    }

    public String lookupBlockId(int protocolId, String itemId) {
        return itemToBlockId.get(protocolId).getOrDefault(itemId, null);
    }
}