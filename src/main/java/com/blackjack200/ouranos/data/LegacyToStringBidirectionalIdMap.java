package com.blackjack200.ouranos.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.val;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class LegacyToStringBidirectionalIdMap extends AbstractMapping {
    private final Map<Integer, Map<Integer, String>> intToStringMap = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Integer>> stringToIntMap = new LinkedHashMap<>();

    public LegacyToStringBidirectionalIdMap(String file) {
        load(file, (protocolId, rawData) -> {
            Map<String, Integer> data = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, Integer>>() {
            }.getType());
            val stringToInt = new LinkedHashMap<String, Integer>();
            val intToString = new LinkedHashMap<Integer, String>();
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