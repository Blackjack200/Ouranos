package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemTypeDictionary extends AbstractMapping {

    @Getter
    private static final ItemTypeDictionary instance = new ItemTypeDictionary();

    private final static Map<Integer, InnerEntry> entries = new ConcurrentHashMap<>();

    private ItemTypeDictionary() {
    }

    public static InnerEntry getInstance(int protocolId) {
        return entries.computeIfAbsent(protocolId, (protocol) -> new InnerEntry(open(lookupAvailableFile("required_item_list.json", protocol))));
    }

    public static class InnerEntry {
        private final Map<String, Integer> stringToRuntimeId;
        private final Map<Integer, String> runtimeIdToString;
        private final Map<String, ItemTypeInfo> allEntries;

        private InnerEntry(InputStream input) {
            this.allEntries = new Gson().fromJson(new InputStreamReader(input), new TypeToken<Map<String, ItemTypeInfo>>() {
            }.getType());
            this.stringToRuntimeId = new ConcurrentHashMap<>();
            this.runtimeIdToString = new ConcurrentHashMap<>();
            allEntries.forEach((stringId, info) -> {
                stringToRuntimeId.put(stringId, info.runtime_id());
                runtimeIdToString.put(info.runtime_id(), stringId);
            });
        }

        public String fromIntId(int itemId) {
            return runtimeIdToString.get(itemId);
        }

        public Integer fromStringId(String itemId) {
            return stringToRuntimeId.get(itemId);
        }

        public Map<String, ItemTypeInfo> getEntries() {
            return allEntries;
        }
    }
}
