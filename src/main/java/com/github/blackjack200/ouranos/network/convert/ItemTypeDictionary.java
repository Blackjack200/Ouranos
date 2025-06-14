package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
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
                try (var requiredItemList = open(lookupAvailableFile("required_item_list.json", protocol));
                     var reader = new InputStreamReader(requiredItemList)) {
                    return new InnerEntry(new Gson().fromJson(reader, new TypeToken<Map<String, ItemTypeInfo>>() {
                    }.getType()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            try (var itemIdMap = open(lookupAvailableFile("item_id_map.json", protocol));
                 var reader = new InputStreamReader(itemIdMap)) {
                Map<String, Integer> rawEntries = new Gson().fromJson(reader, new TypeToken<Map<String, Integer>>() {
                }.getType());
                var entries = new HashMap<String, ItemTypeInfo>(rawEntries.size());
                rawEntries.forEach((key, value) -> {
                    entries.put(key, new ItemTypeInfo(value, false, ItemVersion.LEGACY.ordinal(), null));
                });
                return new InnerEntry(entries);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static class InnerEntry {
        private final Map<String, Integer> stringToRuntimeId;
        private final Map<Integer, String> runtimeIdToString;
        private final Map<String, ItemTypeInfo> allEntries;

        private InnerEntry(Map<String, ItemTypeInfo> entries) {
            this.allEntries = entries;
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
