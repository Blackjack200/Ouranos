package com.blackjack200.ouranos.data.bedrock.item.upgrade;

import lombok.Getter;

import java.util.Map;

/**
 * Class representing the schema for upgrading item IDs and metadata.
 * This schema includes renamed item IDs and remapped metadata.
 */
public record ItemIdMetaUpgradeSchema(@Getter Map<String, String> renamedIds,
                                      @Getter Map<String, Map<Integer, String>> remappedMetas, @Getter int schemaId) {
    /**
     * Constructor to initialize the schema with renamed IDs, remapped metas, and schema ID.
     *
     * @param renamedIds    a map of renamed item IDs
     * @param remappedMetas a map of remapped metadata for item IDs
     * @param schemaId      the schema ID
     */
    public ItemIdMetaUpgradeSchema {
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

