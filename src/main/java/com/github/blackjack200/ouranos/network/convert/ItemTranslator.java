package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.data.LegacyItemIdToStringIdMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log4j2
public final class ItemTranslator extends AbstractMapping {
    public final static class Entry {
        private static ItemTranslator instance;

        private final Map<Integer, Integer> simpleCoreToNetMapping = new HashMap<>();
        private final Map<Integer, Integer> simpleNetToCoreMapping = new HashMap<>();

        private final Map<Integer, Map<Integer, Integer>> complexCoreToNetMapping = new HashMap<>();
        private final Map<Integer, int[]> complexNetToCoreMapping = new HashMap<>();

        private Entry(int protocol, Map<String, Integer> simpleMappings, Map<String, int[]> complexMappings) {
            for (var entry : ItemTypeDictionary.getInstance(protocol).getEntries().entrySet()) {
                String stringId = entry.getKey();
                int netId = entry.getValue().runtime_id();

                if (complexMappings.containsKey(stringId)) {
                    int[] mapping = complexMappings.get(stringId);
                    int id = mapping[0];
                    int meta = mapping[1];

                    complexCoreToNetMapping.computeIfAbsent(id, k -> new HashMap<>()).put(meta, netId);
                    complexNetToCoreMapping.put(netId, new int[]{id, meta});
                } else if (simpleMappings.containsKey(stringId)) {
                    int internalId = simpleMappings.get(stringId);
                    simpleCoreToNetMapping.put(internalId, netId);
                    simpleNetToCoreMapping.put(netId, internalId);
                }
            }
        }

        public Optional<int[]> toNetworkIdQuiet(int internalId, int internalMeta) {
            if (internalMeta == -1) {
                internalMeta = 0x7FFF;
            }

            if (complexCoreToNetMapping.containsKey(internalId) && complexCoreToNetMapping.get(internalId).containsKey(internalMeta)) {
                return Optional.of(new int[]{complexCoreToNetMapping.get(internalId).get(internalMeta), 0});
            }

            if (simpleCoreToNetMapping.containsKey(internalId)) {
                return Optional.of(new int[]{simpleCoreToNetMapping.get(internalId), internalMeta});
            }

            return Optional.empty();
        }

        public int[] toNetworkId(int internalId, int internalMeta) {
            return toNetworkIdQuiet(internalId, internalMeta)
                    .orElseThrow(() -> new IllegalArgumentException("Unmapped ID/metadata combination " + internalId + ":" + internalMeta));
        }

        public int[] fromNetworkId(int networkId, int networkMeta) throws TypeConversionException {
            if (complexNetToCoreMapping.containsKey(networkId)) {
                if (networkMeta != 0) {
                    throw new TypeConversionException("Unexpected non-zero network meta on complex item mapping");
                }
                return complexNetToCoreMapping.get(networkId);
            }

            if (simpleNetToCoreMapping.containsKey(networkId)) {
                return new int[]{simpleNetToCoreMapping.get(networkId), networkMeta};
            }

            throw new TypeConversionException("Unmapped network ID/metadata combination " + networkId + ":" + networkMeta);
        }

        public int[] fromNetworkIdWithWildcardHandling(int networkId, int networkMeta) throws TypeConversionException {
            if (networkMeta != 0x7FFF) {
                return fromNetworkId(networkId, networkMeta);
            }

            int[] result = fromNetworkId(networkId, 0);
            int id = result[0];
            int meta = result[1];
            return new int[]{id, complexNetToCoreMapping.containsKey(networkId) ? meta : -1};
        }

        public static class TypeConversionException extends RuntimeException {
            public TypeConversionException(String message) {
                super(message);
            }
        }
    }

    private static ItemTranslator.Entry make(int protocol, InputStream stream) {
        var gson = new Gson();

        JsonObject json;
        try {
            json = gson.fromJson(new InputStreamReader(stream), JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Invalid item table format", e);
        }

        if (!json.has("simple") || !json.has("complex")) {
            throw new RuntimeException("Invalid item table format");
        }

        var legacyStringToIntMap = LegacyItemIdToStringIdMap.getInstance();

        Map<String, Integer> simpleMappings = new HashMap<>();
        var simpleJson = json.getAsJsonObject("simple");

        for (var entry : simpleJson.entrySet()) {
            String oldId = entry.getKey();
            String newId = entry.getValue().getAsString();
            Integer intId = legacyStringToIntMap.fromString(protocol, oldId);
            if (intId == null) {
                continue;
            }
            simpleMappings.put(newId, intId);
        }

        for (var entry : legacyStringToIntMap.getStringToIntMap(protocol).entrySet()) {
            String stringId = entry.getKey();
            if (simpleMappings.containsKey(stringId)) {
                throw new IllegalStateException("Old ID " + stringId + " collides with new ID");
            }
            simpleMappings.put(stringId, entry.getValue());
        }

        Map<String, int[]> complexMappings = new HashMap<>();
        var complexJson = json.getAsJsonObject("complex");

        for (var entry : complexJson.entrySet()) {
            var oldId = entry.getKey();
            var metaMap = entry.getValue().getAsJsonObject();
            for (var metaEntry : metaMap.entrySet()) {
                int meta = Integer.parseInt(metaEntry.getKey());
                var newId = metaEntry.getValue().getAsString();

                var intId = legacyStringToIntMap.fromString(protocol, oldId);
                if (intId == null) {
                    continue;
                }
                if (complexMappings.containsKey(newId)) {
                    int[] existing = complexMappings.get(newId);
                    if (existing[0] == intId && existing[1] <= meta) {
                        continue;
                    }
                }
                complexMappings.put(newId, new int[]{intId, meta});
            }
        }

        return new ItemTranslator.Entry(protocol, simpleMappings, complexMappings);
    }

    private static final Map<Integer, Entry> entries = new HashMap<>();

    public static Entry getInstance(int protocol) {
        if (!entries.containsKey(protocol)) {
            entries.put(protocol, make(protocol, open(lookupAvailableFile("r16_to_current_item_map.json", protocol))));
        }
        return entries.get(protocol);
    }
}