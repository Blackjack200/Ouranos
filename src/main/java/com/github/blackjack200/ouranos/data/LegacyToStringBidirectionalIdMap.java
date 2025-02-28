package com.github.blackjack200.ouranos.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntRBTreeMap;
import lombok.val;

import java.io.InputStreamReader;
import java.util.Map;

public abstract class LegacyToStringBidirectionalIdMap extends AbstractMapping {
    private final Int2ObjectArrayMap<Map<Integer, String>> intToStringMap = new Int2ObjectArrayMap<>();
    private final Int2ObjectArrayMap<Map<String, Integer>> stringToIntMap = new Int2ObjectArrayMap<>();

    public LegacyToStringBidirectionalIdMap(String file) {
        load(file, (protocolId, rawData) -> {
            Map<String, Integer> data = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, Integer>>() {
            }.getType());
            val stringToInt = new Object2IntRBTreeMap<String>();
            val intToString = new Int2ObjectRBTreeMap<String>();
            data.forEach((stringId, numericId) -> {
                stringToInt.put(stringId, numericId);
                intToString.put(numericId, stringId);
            });
            this.intToStringMap.put(protocolId, intToString);
            this.stringToIntMap.put(protocolId, stringToInt);
        });
    }

    public String fromNumeric(int protocolId, int id) {
        return this.intToStringMap.get(protocolId).get(id);
    }

    public Integer fromString(int protocolId, String id) {
        return this.stringToIntMap.get(protocolId).get(id);
    }

    public Map<String, Integer> getStringToIntMap(int protocolId) {
        return stringToIntMap.get(protocolId);
    }
}