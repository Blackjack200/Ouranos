package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;

import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemTypeDictionary extends AbstractMapping {

    @Getter
    private static final ItemTypeDictionary instance = new ItemTypeDictionary();

    private final static Map<Integer, InnerEntry> entries = new ConcurrentHashMap<>();

    private ItemTypeDictionary() {
    }

    public static InnerEntry getInstance(int protocolId) {
        return entries.computeIfAbsent(protocolId, (protocol) -> {
            if (protocol > Bedrock_v408.CODEC.getProtocolVersion()) {
                Map<String, ItemTypeInfo> map = new Gson().fromJson(new InputStreamReader(open(lookupAvailableFile("required_item_list.json", protocol))), new TypeToken<Map<String, ItemTypeInfo>>() {
                }.getType());
                var newMap = new HashMap<String, ItemDefinition>(map.size());
                for (var entry : map.entrySet()) {
                    newMap.put(entry.getKey(), entry.getValue().toDefinition(entry.getKey()));
                }
                return new InnerEntry(newMap);
            }
            Map<String, Integer> rawEntries = new Gson().fromJson(new InputStreamReader(open(lookupAvailableFile("item_id_map.json", protocol))), new TypeToken<Map<String, Integer>>() {
            }.getType());
            var entries = new HashMap<String, ItemDefinition>(rawEntries.size());
            rawEntries.forEach((key, value) -> {
                entries.put(key, new ItemTypeInfo(value, false, ItemVersion.LEGACY.ordinal(), null).toDefinition(key));
            });
            return new InnerEntry(entries);
        });
    }

    public static class InnerEntry {
        private final Map<String, Integer> stringToRuntimeId;
        private final Map<Integer, String> runtimeIdToString;
        private final Map<String, ItemDefinition> allEntries;

        public InnerEntry(Map<String, ItemDefinition> entries) {
            this.allEntries = new HashMap<>(entries.size());
            this.stringToRuntimeId = new HashMap<>(entries.size());
            this.runtimeIdToString = new HashMap<>(entries.size());
            this.adjust(entries.values());
        }

        public InnerEntry(List<ItemDefinition> entries) {
            this.allEntries = new HashMap<>(entries.size());
            this.stringToRuntimeId = new HashMap<>(entries.size());
            this.runtimeIdToString = new HashMap<>(entries.size());
            this.adjust(entries);
        }

        public String fromIntId(int itemId) {
            return runtimeIdToString.get(itemId);
        }

        public Integer fromStringId(String itemId) {
            return stringToRuntimeId.get(itemId);
        }

        public Map<String, ItemDefinition> getEntries() {
            return allEntries;
        }

        public void adjust(Collection<ItemDefinition> list) {
            list.forEach(def -> {
                this.allEntries.put(def.getIdentifier(), def);
            });
            list.forEach((def) -> {
                stringToRuntimeId.put(def.getIdentifier(), def.getRuntimeId());
                runtimeIdToString.put(def.getRuntimeId(), def.getIdentifier());
            });
        }

        public void clear() {
            this.allEntries.clear();
            this.stringToRuntimeId.clear();
            this.runtimeIdToString.clear();
        }
    }
}
