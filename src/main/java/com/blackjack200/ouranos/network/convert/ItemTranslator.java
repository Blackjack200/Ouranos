package com.blackjack200.ouranos.network.convert;

import cn.hutool.core.convert.Convert;
import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class ItemTranslator extends AbstractMapping {
    @Getter
    private static final ItemTranslator instance;

    static {
        instance = new ItemTranslator();
    }

    private final Map<Integer, Map<String, String>> alias = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> simpleCoreToNetMap = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Integer>> simpleNetToCoreMap = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Map<Integer, Integer>>> complexCoreToNetMap = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, int[]>> complexNetToCoreMap = new LinkedHashMap<>();

    public ItemTranslator() {
        load("r16_to_current_item_map.json", (protocolId, rawData) -> {
            Map<String, Map<String, Object>> data = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, Map<String, Object>>>() {
            }.getType());

            LegacyItemIdToStringIdMap stringIdMap = LegacyItemIdToStringIdMap.getInstance();
            alias.put(protocolId, new LinkedHashMap<>());
            var aliasMap = this.alias.get(protocolId);
            simpleCoreToNetMap.put(protocolId, new LinkedHashMap<>());
            simpleNetToCoreMap.put(protocolId, new LinkedHashMap<>());
            complexCoreToNetMap.put(protocolId, new LinkedHashMap<>());
            complexNetToCoreMap.put(protocolId, new LinkedHashMap<>());

            val simpleMapping = new LinkedHashMap<String, Integer>();

            Convert.toMap(String.class, String.class, data.get("simple")).forEach((oldId, newId) -> {
                //log.info("p={} old={} new={}", protocolId, oldId, newId);
                var intId = stringIdMap.fromString(protocolId, oldId);
                if (intId == null) {
                    //new item without a fixed legacy ID - we can't handle this right now
                    return;
                }
                simpleMapping.put(newId, intId);
                aliasMap.put(oldId, newId);
            });

            stringIdMap.getStringToIntMap(protocolId).forEach((stringId, intId) -> {
                if (simpleMapping.containsKey(stringId)) {
                    throw new RuntimeException("Old ID " + stringId + " collides with new ID");
                }
                simpleMapping.put(stringId, intId);
            });

            val complexMapping = new LinkedHashMap<String, int[]>();
            data.get("complex").forEach((oldId, obj) -> {
                Map<String, String> map = Convert.toMap(String.class, String.class, obj);
                var intId = stringIdMap.fromString(protocolId, oldId);
                if (intId == null) {
                    //new item without a fixed legacy ID - we can't handle this right now
                    return;
                }
                map.forEach((meta, newId) -> {
                    int intMeta = Integer.parseInt(meta);
                    complexMapping.put(newId, new int[]{intId, intMeta});
                    log.debug("p={} old={} cmplx={}", protocolId, oldId, complexMapping.get(newId));
                });
            });


            ItemTypeDictionary.getInstance().getEntries(protocolId).forEach((stringId, d) -> {
                int netId = d.runtime_id();
                if (complexMapping.containsKey(stringId)) {
                    int[] dd = complexMapping.get(stringId);
                    this.complexCoreToNetMap.get(protocolId).putIfAbsent(dd[0], new LinkedHashMap<>());
                    this.complexCoreToNetMap.get(protocolId).get(dd[0]).put(dd[1], netId);

                    this.complexNetToCoreMap.get(protocolId).put(netId, dd);
                } else if (simpleMapping.containsKey(stringId)) {
                    this.simpleCoreToNetMap.get(protocolId).put(simpleMapping.get(stringId), netId);
                    this.simpleNetToCoreMap.get(protocolId).put(netId, simpleMapping.get(stringId));
                }
            });
        });
    }

    public int[] toNetworkIdQuiet(int protocolId, int internalId, int internalMeta) {
        if (internalMeta == -1) {
            internalMeta = 0x7fff;
        }
        val simple = this.simpleCoreToNetMap.get(protocolId);
        val complex = this.complexCoreToNetMap.get(protocolId);
        if (complex.containsKey(internalId) && complex.get(internalId).containsKey(internalMeta)) {
            return new int[]{complex.get(internalId).get(internalMeta), 0};
        }
        if (simple.containsKey(internalId)) {
            return new int[]{simple.get(internalId), internalMeta};
        }
        return null;
    }

    public int[] toNetworkId(int protocolId, int internalId, int internalMeta) {
        return Objects.requireNonNull(this.toNetworkIdQuiet(protocolId, internalId, internalMeta));
    }

    public int[] fromNetworkId(int protocolId, int networkId, int networkMeta) {
        val simple = this.simpleNetToCoreMap.get(protocolId);
        val complex = this.complexNetToCoreMap.get(protocolId);
        if (complex.containsKey(networkId)) {
            if (networkMeta != 0) {
                throw new RuntimeException("Unexpected non-zero network meta on complex item mapping");
            }
            return complex.get(networkId);
        }
        if (simple.containsKey(networkId)) {
            return new int[]{simple.get(networkId), networkMeta};
        }
        throw new RuntimeException("Unmapped network ID/metadata combination " + networkId + ":" + networkMeta);
    }

    public boolean isComplex(int protocolId, int networkId, int networkMeta) {
        val simple = this.simpleNetToCoreMap.get(protocolId);
        val complex = this.complexNetToCoreMap.get(protocolId);
        if (complex.containsKey(networkId)) {
            return true;
        }
        return false;
    }

    public int[] fromNetworkIdNotNull(int protocolId, int networkId, int networkMeta) {
        return Objects.requireNonNull(this.fromNetworkId(protocolId, networkId, networkMeta));
    }

    public int[] fromNetworkIdWithWildcardHandling(int protocolId, int networkId, int networkMeta) {
        if (networkMeta != 0x7fff) {
            return this.fromNetworkId(protocolId, networkId, networkMeta);
        }
        val isComplex = isComplex(protocolId, networkId, networkMeta);
        int[] data = this.fromNetworkIdNotNull(protocolId, networkId, networkMeta);
        return new int[]{data[0], isComplex ? data[1] : -1};
    }

    public String getAlias(int protocol, String id) {
        return this.alias.get(protocol).getOrDefault(id, id);
    }
}