package com.github.blackjack200.ouranos.data.bedrock.item.downgrade;

import cn.hutool.core.util.ArrayUtil;
import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;

import java.util.HashMap;
import java.util.Map;

public class ItemIdMetaDowngrader {
    private final Map<String, String> renamedIds = new HashMap<>();
    private final Map<String, Object[]> remappedMetas = new HashMap<>();

    public ItemIdMetaDowngrader(ItemTypeDictionary.InnerEntry dictionary, int schemaId) {
        var upgrader = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader();

        var networkIds = new HashMap<>();
        for (var entry : upgrader.getSchemas().entrySet()) {
            int id = entry.getKey();
            var schema = entry.getValue();

            if (id <= schemaId) {
                continue;
            }

            for (var renamedIdEntry : schema.renamedIds().entrySet()) {
                String oldId = renamedIdEntry.getKey();
                String newStringId = renamedIdEntry.getValue();

                if (networkIds.containsKey(oldId)) {
                    networkIds.put(newStringId, networkIds.get(oldId));
                } else {
                    if (dictionary.fromStringId(oldId) != null) {
                        networkIds.put(newStringId, oldId);
                    }
                }
            }

            for (var remappedMetasEntry : schema.remappedMetas().entrySet()) {
                String oldId = remappedMetasEntry.getKey();
                var metaToNewId = remappedMetasEntry.getValue();

                if (networkIds.containsKey(oldId)) {
                    for (var metaEntry : metaToNewId.entrySet()) {
                        String oldMeta = metaEntry.getKey().toString();
                        String newStringId = metaEntry.getValue();
                        networkIds.put(newStringId, new Object[]{networkIds.get(oldId), oldMeta});
                    }
                } else {
                    if (dictionary.fromStringId(oldId) != null) {
                        for (var metaEntry : metaToNewId.entrySet()) {
                            String oldMeta = metaEntry.getKey().toString();
                            String newStringId = metaEntry.getValue();
                            networkIds.put(newStringId, new String[]{oldId, oldMeta});
                        }
                    }
                }
            }
        }

        for (var networkIdEntry : networkIds.entrySet()) {
            String newStringId = networkIdEntry.getKey().toString();
            Object oldId = networkIdEntry.getValue();

            if (oldId instanceof Object[]) {
                this.remappedMetas.put(newStringId, ArrayUtil.cast(String.class, oldId));
            } else {
                this.renamedIds.put(newStringId, (String) oldId);
            }
        }
    }

    public Object[] downgrade(String id, int meta) {
        var newId = id;
        var newMeta = meta;

        if (this.remappedMetas.containsKey(newId)) {
            var oldData = this.remappedMetas.get(newId);
            newId = (String) oldData[0];
            newMeta = Integer.parseInt((String) oldData[1]);
        } else if (this.renamedIds.containsKey(newId)) {
            newId = this.renamedIds.get(newId);
        }

        return new Object[]{newId, newMeta};
    }
}

