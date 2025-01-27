package com.blackjack200.ouranos.network.convert;

import cn.hutool.core.convert.Convert;
import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BlockStringIdToItemStringMap extends AbstractMapping {
    //r16_to_current_item_map.json
    @Getter
    private static final BlockStringIdToItemStringMap instance;

    static {
        instance = new BlockStringIdToItemStringMap();
    }

    private Map<Integer, Map<String, String>> blockToItem = new HashMap<>();
    private Map<Integer, Map<String, String>> itemToBlock = new HashMap<>();

    public BlockStringIdToItemStringMap() {
        load("r16_to_current_item_map.json", (protocolId, rawData) -> {
            Map<String, Object> g = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, Object>>() {
            }.getType());
            var blockToItem = Convert.toMap(String.class, String.class, g.get("simple"));
            var itemToBlock = new HashMap<String, String>();
            blockToItem.forEach((key, value) -> {
                itemToBlock.put(value, key);
            });
            this.blockToItem.put(protocolId, blockToItem);
            this.itemToBlock.put(protocolId, itemToBlock);
        });
    }

    public String toItem(int protocolId, String id) {
        return blockToItem.get(protocolId).getOrDefault(id, id);
    }

    public String toBlock(int protocolId, String id) {
        return itemToBlock.get(protocolId).getOrDefault(id, id);
    }
}
