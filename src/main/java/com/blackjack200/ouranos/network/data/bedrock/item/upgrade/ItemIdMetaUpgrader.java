package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Upgrades old item string IDs and metas to newer ones according to the given schemas.
 */
public final class ItemIdMetaUpgrader {

    // Stores the schemas
    private final Map<Integer, ItemIdMetaUpgradeSchema> idMetaUpgradeSchemas = new TreeMap<>();

    /**
     * Constructor to initialize with an array of schemas.
     *
     * @param idMetaUpgradeSchemas the array of ItemIdMetaUpgradeSchema objects
     */
    public ItemIdMetaUpgrader(Collection<ItemIdMetaUpgradeSchema> idMetaUpgradeSchemas) {
        for (ItemIdMetaUpgradeSchema schema : idMetaUpgradeSchemas) {
            this.addSchema(schema);
        }
    }

    /**
     * Adds a schema to the list, ensuring no duplicate schema IDs.
     *
     * @param schema the ItemIdMetaUpgradeSchema to add
     */
    public void addSchema(ItemIdMetaUpgradeSchema schema) {
        if (this.idMetaUpgradeSchemas.containsKey(schema.getSchemaId())) {
            throw new IllegalArgumentException("Already have a schema with priority " + schema.getSchemaId());
        }
        this.idMetaUpgradeSchemas.put(schema.getSchemaId(), schema);
    }

    /**
     * Returns the list of schemas.
     *
     * @return the list of ItemIdMetaUpgradeSchema objects
     */
    public Map<Integer, ItemIdMetaUpgradeSchema> getSchemas() {
        return this.idMetaUpgradeSchemas;
    }

    /**
     * Upgrades the given item ID and meta based on the available schemas.
     *
     * @param id   the item ID to upgrade
     * @param meta the item meta to upgrade
     * @return an array containing the new item ID and meta
     */
    public Object[] upgrade(String id, int meta) {
        String newId = id;
        int newMeta = meta;

        for (ItemIdMetaUpgradeSchema schema : this.idMetaUpgradeSchemas.values()) {
            // First try remapping the meta
            String remappedMetaId = schema.remapMeta(newId, newMeta);
            if (remappedMetaId != null) {
                newId = remappedMetaId;
                newMeta = 0;
            } else {
                // Then try renaming the item ID
                String renamedId = schema.renameId(newId);
                if (renamedId != null) {
                    newId = renamedId;
                }
            }
        }

        return new Object[]{newId, newMeta};
    }
}