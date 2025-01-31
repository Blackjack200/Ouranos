package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import lombok.Data;
import lombok.Getter;
import org.cloudburstmc.nbt.NbtMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Data
public final class BlockStateUpgradeSchema {

    // Getters for maps/lists (optional, depending on use case)
    // Maps for renamed, added, removed, renamed properties
    public Map<String, String> renamedIds = new HashMap<>();
    public Map<String, NbtMap> addedProperties = new HashMap<>();
    public Map<String, List<String>> removedProperties = new HashMap<>();
    public Map<String, Map<String, String>> renamedProperties = new HashMap<>();
    public Map<String, Map<String, List<BlockStateUpgradeSchemaValueRemap>>> remappedPropertyValues = new HashMap<>();
    public Map<String, BlockStateUpgradeSchemaFlattenInfo> flattenedProperties = new HashMap<>();
    public Map<String, List<BlockStateUpgradeSchemaBlockRemap>> remappedStates = new HashMap<>();

    // Deprecated: Mojang-defined, use getSchemaId() for internal version management
    public int versionId;
    public int maxVersionMajor;
    public int maxVersionMinor;
    public int maxVersionPatch;
    public int maxVersionRevision;
    public int schemaId;

    /**
     * Constructor.
     *
     * @param maxVersionMajor    The major version.
     * @param maxVersionMinor    The minor version.
     * @param maxVersionPatch    The patch version.
     * @param maxVersionRevision The revision version.
     * @param schemaId           The schema id.
     */
    public BlockStateUpgradeSchema(int maxVersionMajor, int maxVersionMinor, int maxVersionPatch,
                                   int maxVersionRevision, int schemaId) {
        this.maxVersionMajor = maxVersionMajor;
        this.maxVersionMinor = maxVersionMinor;
        this.maxVersionPatch = maxVersionPatch;
        this.maxVersionRevision = maxVersionRevision;
        this.schemaId = schemaId;

        // Calculate versionId by combining the version components
        this.versionId = (this.maxVersionMajor << 24) | (this.maxVersionMinor << 16) |
                (this.maxVersionPatch << 8) | this.maxVersionRevision;
    }

    /**
     * Checks if the schema is empty (i.e., all internal maps/lists are empty).
     *
     * @return true if schema is empty, false otherwise.
     */
    public boolean isEmpty() {
        // Check all the lists/maps for emptiness
        for (var map : Arrays.asList(renamedIds, addedProperties, removedProperties,
                renamedProperties, remappedPropertyValues, flattenedProperties, remappedStates)) {
            if (!map.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
