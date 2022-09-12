package com.blackjack200.ouranos.network.mapping;

import com.blackjack200.ouranos.network.mapping.types.ItemTypeInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2
public class ItemTypeDictionary extends AbstractMapping {
    private static final ItemTypeDictionary instance;

    static {
        instance = new ItemTypeDictionary();
    }

    public static ItemTypeDictionary getInstance() {
        return instance;
    }

    private final Map<Integer, Map<String, Integer>> stringToRuntimeIdMap = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, String>> runtimeIdToStringMap = new LinkedHashMap<>();

    public ItemTypeDictionary() {
        load("required_item_list.json", (protocolId, rawData) -> {
            Map<String, ItemTypeInfo> data = (new Gson()).fromJson(rawData, new TypeToken<Map<String, ItemTypeInfo>>() {
            }.getType());
            Map<String, Integer> stringToRuntime = new LinkedHashMap<>();
            Map<Integer, String> runtimeToString = new LinkedHashMap<>();
            data.forEach((stringId, info) -> {
                stringToRuntime.put(stringId, info.runtime_id);
                runtimeToString.put(info.runtime_id, stringId);
                log.info("p={} k={} id={} cb={}", protocolId, stringId, info.runtime_id, info.component_based);
            });
            this.stringToRuntimeIdMap.put(protocolId, stringToRuntime);
            this.runtimeIdToStringMap.put(protocolId, runtimeToString);
        });
    }

    public String fromNumericId(int protocolId, int itemId) {
        return this.runtimeIdToStringMap.get(protocolId).get(itemId);
    }

    public int fromStringId(int protocolId, String itemId) {
        return this.stringToRuntimeIdMap.get(protocolId).get(itemId);
    }
}
