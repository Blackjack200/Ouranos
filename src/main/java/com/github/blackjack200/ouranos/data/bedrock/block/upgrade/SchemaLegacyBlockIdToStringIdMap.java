package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SchemaLegacyBlockIdToStringIdMap extends AbstractMapping {
    private final Map<Integer, String> intToStringMap = new LinkedHashMap<>();
    private final Map<String, Integer> stringToIntMap = new LinkedHashMap<>();

    public SchemaLegacyBlockIdToStringIdMap() {
        var d = open("upgrade_block/block_legacy_id_map.json");
        Map<String, Integer> data = (new Gson()).fromJson(new InputStreamReader(d), new TypeToken<Map<String, Integer>>() {
        }.getType());
        data.forEach((stringId, numericId) -> {
            this.stringToIntMap.put(stringId, numericId);
            this.intToStringMap.put(numericId, stringId);
        });
    }

    public String fromNumeric(int id) {
        return this.intToStringMap.get(id);
    }

    public Integer fromString(String id) {
        return this.stringToIntMap.get(id);
    }

    private static SchemaLegacyBlockIdToStringIdMap instance;

    public static SchemaLegacyBlockIdToStringIdMap getInstance() {
        if (instance == null) {
            instance = new SchemaLegacyBlockIdToStringIdMap();
        }
        return instance;
    }

    public void add(String stringId, int intId) {
        this.intToStringMap.put(intId, stringId);
        this.stringToIntMap.put(stringId, intId);
    }
}

