package com.github.blackjack200.ouranos.network.convert.biome;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeDefinitionRegistry extends AbstractMapping {
    @Getter
    private static final BiomeDefinitionRegistry instance = new BiomeDefinitionRegistry();

    private final static Map<Integer, BiomeDefinitionRegistry.InnerEntry> entries = new ConcurrentHashMap<>();

    private BiomeDefinitionRegistry() {
    }

    public static BiomeDefinitionRegistry.InnerEntry getInstance(int protocolId) {
        return entries.computeIfAbsent(protocolId, (protocol) -> {
            Map<String, BiomeDefinitionDataBean> rawEntries = new Gson().fromJson(new InputStreamReader(open(lookupAvailableFile("biome_definitions.json", protocol))), new TypeToken<Map<String, BiomeDefinitionDataBean>>() {
            }.getType());
            var entries = new HashMap<String, BiomeDefinitionData>(rawEntries.size());
            rawEntries.forEach((key, value) -> {
                entries.put(key, value.toData());
            });
            return new BiomeDefinitionRegistry.InnerEntry(entries);
        });
    }

    public static class InnerEntry {
        private final Map<String, BiomeDefinitionData> stringToDef;
        private final Map<String, BiomeDefinitionData> allEntries;

        private InnerEntry(Map<String, BiomeDefinitionData> entries) {
            this.allEntries = entries;
            this.stringToDef = new ConcurrentHashMap<>();
            stringToDef.putAll(allEntries);
        }

        public BiomeDefinitionData fromStringId(String itemId) {
            return stringToDef.get(itemId);
        }

        public Map<String, BiomeDefinitionData> getEntries() {
            return allEntries;
        }
    }
}
