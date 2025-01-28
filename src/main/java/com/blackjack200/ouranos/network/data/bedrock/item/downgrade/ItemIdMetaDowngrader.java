package com.blackjack200.ouranos.network.data.bedrock.item.downgrade;

import com.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.blackjack200.ouranos.network.data.bedrock.GlobalItemDataHandlers;

import java.util.HashMap;
import java.util.Map;

public class ItemIdMetaDowngrader {

    private Map<String, String> renamedIds = new HashMap<>();
    private Map<String, Object[]> remappedMetas = new HashMap<>();

    public ItemIdMetaDowngrader(ItemTypeDictionary dictionary, int protocolId, int schemaId) {
        var upgrader = GlobalItemDataHandlers.getUpgrader().getIdMetaUpgrader();

        Map<String, String> networkIds = new HashMap<>();
        for (var entry : upgrader.getSchemas().entrySet()) {
            int id = entry.getKey();
            var schema = entry.getValue();

            if (id <= schemaId) {
                continue;
            }

            for (var renamedIdEntry : schema.getRenamedIds().entrySet()) {
                String oldId = renamedIdEntry.getKey();
                String newStringId = renamedIdEntry.getValue();

                if (networkIds.containsKey(oldId)) {
                    networkIds.put(newStringId, networkIds.get(oldId));
                } else {
                    try {
                        dictionary.fromStringId(protocolId, oldId);
                        networkIds.put(newStringId, oldId);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }
            }

            for (var remappedMetasEntry : schema.getRemappedMetas().entrySet()) {
                String oldId = remappedMetasEntry.getKey();
                var metaToNewId = remappedMetasEntry.getValue();

                if (networkIds.containsKey(oldId)) {
                    for (var metaEntry : metaToNewId.entrySet()) {
                        String oldMeta = metaEntry.getKey().toString();
                        String newStringId = metaEntry.getValue();
                        networkIds.put(newStringId, new Object[]{networkIds.get(oldId), oldMeta});
                    }
                } else {
                    try {
                        dictionary.fromStringId(protocolId, oldId);
                        for (var metaEntry : metaToNewId.entrySet()) {
                            String oldMeta = metaEntry.getKey().to;
                            String newStringId = metaEntry.getValue();
                            networkIds.put(newStringId, new String[]{oldId, oldMeta});
                        }
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }
            }
        }

        for (var networkIdEntry : networkIds.entrySet()) {
            String newStringId = networkIdEntry.getKey();
            Object oldId = networkIdEntry.getValue();

            if (oldId instanceof String[]) {
                this.remappedMetas.put(newStringId, (String[]) oldId);
            } else {
                this.renamedIds.put(newStringId, (String) oldId);
            }
        }
    }

    public int[] downgrade(String id, int meta) {
        var newId = id;
        var newMeta = meta;

        if (this.remappedMetas.containsKey(newId)) {
            var oldData = this.remappedMetas.get(newId);
            newId = (String) oldData[0];
            newMeta = (Integer) oldData[1];
        } else if (this.renamedIds.containsKey(newId)) {
            newId = this.renamedIds.get(newId);
        }

        return new int[]{Integer.parseInt(newId), newMeta};
    }
}

