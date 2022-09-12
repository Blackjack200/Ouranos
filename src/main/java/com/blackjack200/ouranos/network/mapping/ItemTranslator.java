package com.blackjack200.ouranos.network.mapping;

import cn.hutool.core.convert.Convert;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class ItemTranslator extends AbstractMapping {
    private static final ItemTranslator instance;

    static {
        instance = new ItemTranslator();
    }

    public static ItemTranslator getInstance() {
        return instance;
    }
    private Map<Integer, Map<Integer, Integer>> simpleCoreToNetMap = new LinkedHashMap<>();
    private Map<Integer, Map<Integer, Integer>> simpleNetToCoreMap = new LinkedHashMap<>();
    private Map<Integer, Map<Integer, Map<Integer, Integer>>> complexCoreToNetMap = new LinkedHashMap<>();
    private Map<Integer, Map<Integer, int[]>> complexNetToCoreMap = new LinkedHashMap<>();

    public ItemTranslator() {
        load("r16_to_current_item_map.json", (protocolId, rawData) -> {
            Map<String, Map<String, Object>> data = (new Gson()).fromJson(rawData, new TypeToken<Map<String, Map<String, Object>>>() {
            }.getType());
            val simpleMapping = new LinkedHashMap<String, Integer>();
            Convert.toMap(String.class, String.class, data.get("simple")).forEach((oldId, newId) -> {
                log.info("p={} old={} new={}", protocolId, oldId, newId);
                simpleCoreToNetMap.put(protocolId, new LinkedHashMap<>());
                simpleNetToCoreMap.put(protocolId, new LinkedHashMap<>());
                try {
                    int intId = LegacyItemIdToStringIdMap.getInstance().fromString(protocolId, oldId);
                    simpleMapping.put(newId, intId);
                } catch (NullPointerException ignored) {
                }
                simpleMapping.forEach((stringId, coreIntId) -> {
                    try {
                        int netIntId = ItemTypeDictionary.getInstance().fromStringId(protocolId, stringId);
                        this.simpleCoreToNetMap.get(protocolId).put(coreIntId, netIntId);
                        this.simpleNetToCoreMap.get(protocolId).put(netIntId, coreIntId);
                    } catch (NullPointerException ignored) {
                    }
                });
            });
            val complexMapping = new LinkedHashMap<String, int[]>();
            data.get("complex").forEach((oldId, obj) -> {
                Map<String, String> map = Convert.toMap(String.class, String.class, obj);
                complexCoreToNetMap.put(protocolId, new LinkedHashMap<>());
                complexNetToCoreMap.put(protocolId, new LinkedHashMap<>());
                try {
                    int intId = LegacyItemIdToStringIdMap.getInstance().fromString(protocolId, oldId);
                    map.forEach((meta, newId) -> {
                        if (!complexMapping.containsKey(newId)) {
                            complexMapping.put(newId, new int[2]);
                        }
                        complexMapping.get(newId)[0] = intId;
                        int intMeta = Integer.parseInt(meta);
                        complexMapping.get(newId)[1] = intMeta;
                        log.info("p={} old={} cmplx={}", protocolId, oldId, complexMapping.get(newId));
                        if (!complexCoreToNetMap.get(protocolId).containsKey(intId)) {
                            complexCoreToNetMap.get(protocolId).put(intId, new LinkedHashMap<>());
                        }
                        int oldIntId = ItemTypeDictionary.getInstance().fromStringId(protocolId, oldId);
                        complexCoreToNetMap.get(protocolId).get(intId).put(intMeta, oldIntId);
                        complexNetToCoreMap.get(protocolId).put(oldIntId, complexMapping.get(newId));
                    });
                } catch (NullPointerException ignored) {
                }
            });
        });
    }

    public @Nullable int[] toNetworkIdQuiet(int protocolId, int internalId, int internalMeta) {
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

    public @Nullable int[] fromNetworkId(int protocolId, int networkId, int networkMeta) {
        val simple = this.simpleNetToCoreMap.get(protocolId);
        val complex = this.complexNetToCoreMap.get(protocolId);
        if (complex.containsKey(networkId)) {
            if (networkMeta != 0) {
                return null;
            }
            return complex.get(networkId);
        }
        if (simple.containsKey(networkId)) {
            return new int[]{simple.get(networkId), networkMeta};
        }
        return null;
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

    public @Nullable int[] fromNetworkIdWithWildcardHandling(int protocolId, int networkId, int networkMeta) {
        if (networkMeta != 0x7fff) {
            return this.fromNetworkId(protocolId, networkId, networkMeta);
        }
        val isComplex = isComplex(protocolId, networkId, networkMeta);
        int[] data = this.fromNetworkIdNotNull(protocolId, networkId, networkMeta);
        return new int[]{data[0], isComplex ? data[1] : -1};
    }
}
