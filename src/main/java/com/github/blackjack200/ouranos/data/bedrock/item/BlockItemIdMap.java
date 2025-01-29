package com.github.blackjack200.ouranos.data.bedrock.item;

import cn.hutool.core.map.MapUtil;
import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BlockItemIdMap extends AbstractMapping {
    @Getter
    private static final BlockItemIdMap instance;

    static {
        instance = new BlockItemIdMap();
    }

    private final Map<Integer, Map<String, String>> blockToItemId = new HashMap<>();
    private final Map<Integer, Map<String, String>> itemToBlockId = new HashMap<>();

    private BlockItemIdMap() {
        load("block_id_to_item_id_map.json", (protocol, rawData) -> {
            var map = new Gson().fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, String>>() {
            });
            this.blockToItemId.put(protocol, map);
            this.itemToBlockId.put(protocol, MapUtil.reverse(map));
        });
    }

    public String lookupItemId(int protocolId, String blockId) {
        return blockToItemId.get(protocolId).getOrDefault(blockId, null);
    }

    public String lookupBlockId(int protocolId, String itemId) {
        return itemToBlockId.get(protocolId).getOrDefault(itemId, null);
    }
}