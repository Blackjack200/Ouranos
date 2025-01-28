package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import lombok.Getter;

import java.util.Map;

/**
 * Class representing the schema for upgrading item IDs and metadata.
 * This schema includes renamed item IDs and remapped metadata.
 */
public final class ItemIdMetaUpgradeSchema {
    @Getter
    private final Map<String, String> renamedIds;
    @Getter
    private final Map<String, Map<Integer, String>> remappedMetas;
    @Getter
    private final int schemaId;

    /**
     * Constructor to initialize the schema with renamed IDs, remapped metas, and schema ID.
     *
     * @param renamedIds    a map of renamed item IDs
     * @param remappedMetas a map of remapped metadata for item IDs
     * @param schemaId      the schema ID
     */
    public ItemIdMetaUpgradeSchema(Map<String, String> renamedIds,
                                   Map<String, Map<Integer, String>> remappedMetas,
                                   int schemaId) {
        this.renamedIds = renamedIds;
        this.remappedMetas = remappedMetas;
        this.schemaId = schemaId;
    }

    /**
     * Renames the item ID if a renamed version exists.
     *
     * @param id the item ID to rename
     * @return the renamed ID, or null if no renamed version exists
     */
    public String renameId(String id) {
        return this.renamedIds.get(id.toLowerCase());
    }

    /**
     * Remaps the metadata for a given item ID.
     *
     * @param id   the item ID
     * @param meta the metadata value
     * @return the remapped metadata, or null if no remapped version exists
     */
    public String remapMeta(String id, int meta) {
        Map<Integer, String> metas = this.remappedMetas.get(id.toLowerCase());
        if (metas != null) {
            return metas.get(meta);
        }
        return null;
    }
}

