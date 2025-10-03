package com.github.blackjack200.ouranos.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntRBTreeMap;
import lombok.val;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public abstract class LegacyToStringBidirectionalIdMap extends AbstractMapping {
    private final Int2ObjectArrayMap<Map<Integer, String>> intToStringMap = new Int2ObjectArrayMap<>();
    private final Int2ObjectArrayMap<Map<String, Integer>> stringToIntMap = new Int2ObjectArrayMap<>();

    public LegacyToStringBidirectionalIdMap(String file) {
        load(file, (protocolId, rawData) -> {
            try (var reader = new InputStreamReader(rawData)) {
                Map<String, Integer> data = (new Gson()).fromJson(reader, new TypeToken<Map<String, Integer>>() {
                }.getType());
                val stringToInt = new Object2IntRBTreeMap<String>();
                val intToString = new Int2ObjectRBTreeMap<String>();
                data.forEach((stringId, numericId) -> {
                    int primNumId = numericId;
                    stringToInt.put(stringId, primNumId);
                    intToString.put(primNumId, stringId);
                });
                int primProtocolId = protocolId;
                this.intToStringMap.put(primProtocolId, intToString);
                this.stringToIntMap.put(primProtocolId, stringToInt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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