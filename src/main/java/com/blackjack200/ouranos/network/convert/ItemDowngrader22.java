package com.blackjack200.ouranos.network.convert;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class ItemDowngrader22 extends AbstractMapping {
    @Getter
    private static final ItemDowngrader22 instance;

    static {
        instance = new ItemDowngrader22();
    }

    private final Map<Integer, Map<String, String>> simpleNewToOld = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Object[]>> complexNewToOld = new LinkedHashMap<>();

    private static String hash(Object[] a) {
        return a[0] + a[1].toString();
    }

    public ItemDowngrader22() {
        load("r16_to_current_item_map.json", (protocolId, rawData) -> {
            Map<String, Map<String, Object>> data = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<Map<String, Map<String, Object>>>() {
            }.getType());

            LegacyItemIdToStringIdMap stringIdMap = LegacyItemIdToStringIdMap.getInstance();

            simpleNewToOld.put(protocolId, new LinkedHashMap<>());
            complexNewToOld.put(protocolId, new LinkedHashMap<>());

            val simpleOldToNew = Convert.toMap(String.class, String.class, data.get("simple"));
            val simpleNewToOld = MapUtil.reverse(simpleOldToNew);
            val complexNewToOld = new LinkedHashMap<String, Object[]>();

            data.get("complex").forEach((oldId, obj) -> {
                Map<String, String> map = Convert.toMap(String.class, String.class, obj);
                var intId = stringIdMap.fromString(protocolId, oldId);
                if (intId == null) {
                    //new item without a fixed legacy ID - we can't handle this right now
                    return;
                }
                map.forEach((meta, newId) -> {
                    int intMeta = Integer.parseInt(meta);
                    complexNewToOld.put(newId, new Object[]{oldId, intMeta});
                });
            });

            ItemTypeDictionary.getInstance().getEntries(protocolId).forEach((newStringId, d) -> {
                if (complexNewToOld.containsKey(newStringId)) {
                    Object[] dd = complexNewToOld.get(newStringId);
                    this.complexNewToOld.get(protocolId).put(newStringId, dd);
                } else if (simpleNewToOld.containsKey(newStringId)) {
                    this.simpleNewToOld.get(protocolId).put(newStringId, simpleNewToOld.get(newStringId));
                }
            });
        });
    }

    public String mapSimpleId(int protocolId, String newId) {
        return simpleNewToOld.get(protocolId).get(newId);
    }

    public Object[] mapComplexId(int protocolId, String newId) {
        return complexNewToOld.get(protocolId).get(newId);
    }

    public ItemData downgrade(int source, int destination, ItemData item) {
        if (source < destination) {
            throw new RuntimeException("Source protocol " + source + " is lower than destination protocol " + destination);
        }/*
        if (ItemTypeDictionary.getInstance().fromStringId(destination, item.getDefinition().getIdentifier()) != null ||
                ItemTypeDictionary.getInstance().fromStringId(destination, BlockItemIdMap.getInstance().lookupBlockId(destination, item.getDefinition().getIdentifier())) != null ||
                ItemTypeDictionary.getInstance().fromStringId(destination, ItemTranslator.getInstance().getAlias(destination, item.getDefinition().getIdentifier())) != null
        ) {
            val blockDef = item.getBlockDefinition();
            if (blockDef != null) {
                val translatedBlockRuntimeId = translateBlockRuntimeId(source, destination, blockDef.getRuntimeId());
                item = item.toBuilder().blockDefinition(() -> translatedBlockRuntimeId).build();
            }
            newContents.add(item);
        } else {
            try {
                val data = ItemTranslator.getInstance().fromNetworkId(source, item.getDefinition().getRuntimeId(), item.getDamage());
                ItemTranslator.getInstance().toNetworkId(destination, data[0], data[1]);
                ItemTranslator.getInstance().
                        newContents.add(item);
            } catch (RuntimeException e) {
                e.printStackTrace();
                newContents.add(barrier);
            }
        }*/
        return null;
    }
}

