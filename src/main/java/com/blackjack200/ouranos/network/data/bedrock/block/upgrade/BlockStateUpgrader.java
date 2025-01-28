package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import java.util.*;

public final class BlockStateUpgrader {
    /**
     * Maps version ID to a map of schema ID to schema.
     */
    private Map<Integer, Map<Integer, BlockStateUpgradeSchema>> upgradeSchemas = new HashMap<>();
    private int outputVersion = 0;

    /**
     * Constructor that accepts a list of BlockStateUpgradeSchema objects.
     * @param upgradeSchemas List of upgrade schemas
     */
    public BlockStateUpgrader(List<BlockStateUpgradeSchema> upgradeSchemas) {
        for (BlockStateUpgradeSchema schema : upgradeSchemas) {
            addSchema(schema);
        }
    }

    /**
     * Adds a schema to the upgrader.
     * @param schema BlockStateUpgradeSchema
     */
    public void addSchema(BlockStateUpgradeSchema schema) {
        int schemaId = schema.getSchemaId();
        int versionId = schema.getVersionId();

        if (upgradeSchemas.containsKey(versionId) && upgradeSchemas.get(versionId).containsKey(schemaId)) {
            throw new IllegalArgumentException("Cannot add two schemas with the same schema ID and version ID");
        }

        upgradeSchemas
                .computeIfAbsent(versionId, k -> new HashMap<>())
                .put(schemaId, schema);

        upgradeSchemas.keySet().stream().sorted().forEach(v -> {
            upgradeSchemas.get(v).keySet().stream().sorted();
        });

        outputVersion = Math.max(outputVersion, schema.getVersionId());
    }

    /**
     * Applies all relevant schemas to a given BlockStateData.
     * @param blockStateData BlockStateData
     * @return New BlockStateData
     */
    public BlockStateData upgrade(BlockStateData blockStateData) {
        int version = blockStateData.getVersion();
        for (Map.Entry<Integer, Map<Integer, BlockStateUpgradeSchema>> entry : upgradeSchemas.entrySet()) {
            int resultVersion = entry.getKey();
            Map<Integer, BlockStateUpgradeSchema> schemaList = entry.getValue();

            if (version > resultVersion || (schemaList.size() == 1 && version == resultVersion)) {
                continue;
            }

            for (BlockStateUpgradeSchema schema : schemaList.values()) {
                blockStateData = applySchema(schema, blockStateData);
            }
        }

        if (outputVersion > version) {
            return new BlockStateData(blockStateData.getName(), blockStateData.getStates(), outputVersion);
        }

        return blockStateData;
    }

    /**
     * Applies a schema to BlockStateData.
     * @param schema BlockStateUpgradeSchema
     * @param blockStateData BlockStateData
     * @return BlockStateData
     */
    private BlockStateData applySchema(BlockStateUpgradeSchema schema, BlockStateData blockStateData) {
        BlockStateData newStateData = applyStateRemapped(schema, blockStateData);
        if (newStateData != null) {
            return newStateData;
        }

        String oldName = blockStateData.getName();
        Map<String, Tag> states = blockStateData.getStates();
        String newName = null;

        if (schema.getRenamedIds().containsKey(oldName)) {
            newName = schema.getRenamedIds().get(oldName);
        }

        // Apply various property changes
        states = applyPropertyAdded(schema, oldName, states);
        states = applyPropertyRemoved(schema, oldName, states);
        states = applyPropertyRenamedOrValueChanged(schema, oldName, states);
        states = applyPropertyValueChanged(schema, oldName, states);

        if (newName != null || !states.equals(blockStateData.getStates())) {
            return new BlockStateData(newName != null ? newName : oldName, states, schema.getVersionId());
        }

        return blockStateData;
    }

    private BlockStateData applyStateRemapped(BlockStateUpgradeSchema schema, BlockStateData blockStateData) {
        String oldName = blockStateData.getName();
        Map<String, Tag> oldState = blockStateData.getStates();

        if (schema.getRemappedStates().containsKey(oldName)) {
            for (BlockStateUpgradeSchemaBlockRemap remap : schema.getRemappedStates().get(oldName)) {
                if (remap.getOldState().size() > oldState.size()) {
                    continue;
                }
                // Match old state properties
                for (Map.Entry<String, Tag> entry : remap.getOldState().entrySet()) {
                    if (!oldState.containsKey(entry.getKey()) || !oldState.get(entry.getKey()).equals(entry.getValue())) {
                        continue;
                    }
                }

                String newName = remap.getNewName() != null ? remap.getNewName() : oldName;
                Map<String, Tag> newState = new HashMap<>(remap.getNewState());

                // Copy additional state properties
                for (String stateName : remap.getCopiedState()) {
                    if (oldState.containsKey(stateName)) {
                        newState.put(stateName, oldState.get(stateName));
                    }
                }

                return new BlockStateData(newName, newState, schema.getVersionId());
            }
        }

        return null;
    }

    /**
     * Applies added properties to the states.
     * @param schema BlockStateUpgradeSchema
     * @param oldName Old block state name
     * @param states Current states
     * @return Updated states
     */
    private Map<String, Tag> applyPropertyAdded(BlockStateUpgradeSchema schema, String oldName, Map<String, Tag> states) {
        if (schema.getAddedProperties().containsKey(oldName)) {
            for (Map.Entry<String, Tag> entry : schema.getAddedProperties().get(oldName).entrySet()) {
                states.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return states;
    }

    /**
     * Applies removed properties to the states.
     * @param schema BlockStateUpgradeSchema
     * @param oldName Old block state name
     * @param states Current states
     * @return Updated states
     */
    private Map<String, Tag> applyPropertyRemoved(BlockStateUpgradeSchema schema, String oldName, Map<String, Tag> states) {
        if (schema.getRemovedProperties().containsKey(oldName)) {
            for (String property : schema.getRemovedProperties().get(oldName)) {
                states.remove(property);
            }
        }
        return states;
    }

    /**
     * Applies renamed or changed property values.
     * @param schema BlockStateUpgradeSchema
     * @param oldName Old block state name
     * @param states Current states
     * @return Updated states
     */
    private Map<String, Tag> applyPropertyRenamedOrValueChanged(BlockStateUpgradeSchema schema, String oldName, Map<String, Tag> states) {
        if (schema.getRenamedProperties().containsKey(oldName)) {
            for (Map.Entry<String, String> entry : schema.getRenamedProperties().get(oldName).entrySet()) {
                Tag oldValue = states.get(entry.getKey());
                if (oldValue != null) {
                    states.remove(entry.getKey());
                    states.put(entry.getValue(), oldValue);
                }
            }
        }
        return states;
    }

    /**
     * Applies changed property values.
     * @param schema BlockStateUpgradeSchema
     * @param oldName Old block state name
     * @param states Current states
     * @return Updated states
     */
    private Map<String, Tag> applyPropertyValueChanged(BlockStateUpgradeSchema schema, String oldName, Map<String, Tag> states) {
        if (schema.getRemappedPropertyValues().containsKey(oldName)) {
            for (Map.Entry<String, List<ValueRemap>> entry : schema.getRemappedPropertyValues().get(oldName).entrySet()) {
                Tag oldValue = states.get(entry.getKey());
                if (oldValue != null) {
                    for (ValueRemap remap : entry.getValue()) {
                        if (oldValue.equals(remap.getOld())) {
                            states.put(entry.getKey(), remap.getNew());
                        }
                    }
                }
            }
        }
        return states;
    }
}
