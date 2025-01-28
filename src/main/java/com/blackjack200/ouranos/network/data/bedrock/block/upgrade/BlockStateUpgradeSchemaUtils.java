package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model.BlockStateUpgradeSchemaModel;
import com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model.BlockStateUpgradeSchemaModelTag;
import com.google.gson.Gson;

import java.util.*;

public final class BlockStateUpgradeSchemaUtils {

    private static final Gson gson = new Gson();

    public static String describe(BlockStateUpgradeSchema schema) {
        List<String> lines = new ArrayList<>();

        lines.add("Renames:");
        for (String rename : schema.renamedIds) {
            lines.add("- " + rename);
        }

        lines.add("Added properties:");
        for (Map.Entry<String, Map<String, Tag>> entry : schema.addedProperties.entrySet()) {
            String blockName = entry.getKey();
            for (Map.Entry<String, Tag> tagEntry : entry.getValue().entrySet()) {
                lines.add("- " + blockName + " has " + tagEntry.getKey() + " added: " + tagEntry.getValue());
            }
        }

        lines.add("Removed properties:");
        for (Map.Entry<String, List<String>> entry : schema.removedProperties.entrySet()) {
            String blockName = entry.getKey();
            for (String tagName : entry.getValue()) {
                lines.add("- " + blockName + " has " + tagName + " removed");
            }
        }

        lines.add("Renamed properties:");
        for (Map.Entry<String, Map<String, String>> entry : schema.renamedProperties.entrySet()) {
            String blockName = entry.getKey();
            for (Map.Entry<String, String> tagEntry : entry.getValue().entrySet()) {
                lines.add("- " + blockName + " has " + tagEntry.getKey() + " renamed to " + tagEntry.getValue());
            }
        }

        lines.add("Remapped property values:");
        for (Map.Entry<String, Map<String, List<BlockStateUpgradeSchemaValueRemap>>> entry : schema.remappedPropertyValues.entrySet()) {
            String blockName = entry.getKey();
            for (Map.Entry<String, List<BlockStateUpgradeSchemaValueRemap>> remapEntry : entry.getValue().entrySet()) {
                for (BlockStateUpgradeSchemaValueRemap remap : remapEntry.getValue()) {
                    lines.add("- " + blockName + " has " + remapEntry.getKey() + " value changed from " + remap.old + " to " + remap.new);
                }
            }
        }

        return String.join("\n", lines);
    }

    public static BlockStateUpgradeSchemaModelTag tagToJsonModel(Tag tag) {
        BlockStateUpgradeSchemaModelTag model = new BlockStateUpgradeSchemaModelTag();
        if (tag instanceof IntTag) {
            model.setInt(((IntTag) tag).getValue());
        } else if (tag instanceof StringTag) {
            model.setString(((StringTag) tag).getValue());
        } else if (tag instanceof ByteTag) {
            model.setByte(((ByteTag) tag).getValue());
        } else {
            throw new IllegalArgumentException("Unexpected value type " + tag.getClass().getName());
        }

        return model;
    }

    // Static method to parse the schema from JSON
    public static BlockStateUpgradeSchema fromJsonModel(BlockStateUpgradeSchemaModel model, int schemaId) {
        var result = new BlockStateUpgradeSchema(
                model.getMaxVersionMajor(),
                model.getMaxVersionMinor(),
                model.getMaxVersionPatch(),
                model.getMaxVersionRevision(),
                schemaId
        );
        result = model.renamedIds != null ? model.renamedIds : new HashMap<>();
        result.renamedProperties = model.renamedProperties != null ? model.renamedProperties : new HashMap<>();
        result.removedProperties = model.removedProperties != null ? model.removedProperties : new HashMap<>();

        // Added Properties Mapping
        for (var entry : model.addedProperties.entrySet()) {
            String blockName = entry.getKey();
            var properties = entry.getValue();
            var processedProperties = new HashMap<>();
            for (var property : properties.entrySet()) {
                processedProperties.put(property.getKey(), jsonModelToTag(property.getValue()));
            }
            result.addedProperties.put(blockName, processedProperties);
        }

        // Remapped Values Index
        Map<String, List<BlockStateUpgradeSchemaValueRemap>> remappedValuesIndex = new HashMap<>();
        for (Map.Entry<String, List<BlockStateUpgradeSchemaValueRemap>> entry : model.remappedPropertyValuesIndex.entrySet()) {
            for (BlockStateUpgradeSchemaValueRemap oldNew : entry.getValue()) {
                remappedValuesIndex.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(new BlockStateUpgradeSchemaValueRemap(jsonModelToTag(oldNew.old), jsonModelToTag(oldNew.newTag)));
            }
        }

        // Remapped Property Values Mapping
        for (Map.Entry<String, Map<String, String>> entry : model.remappedPropertyValues.entrySet()) {
            String blockName = entry.getKey();
            Map<String, String> properties = entry.getValue();
            for (Map.Entry<String, String> property : properties.entrySet()) {
                String mappingKey = property.getValue();
                if (!remappedValuesIndex.containsKey(mappingKey)) {
                    throw new JsonSyntaxException("Missing key from schema values index " + mappingKey);
                }
                result.remappedPropertyValues.putIfAbsent(blockName, new ArrayList<>());
                result.remappedPropertyValues.get(blockName).add(remappedValuesIndex.get(mappingKey));
            }
        }

        // Flattened Properties Mapping
        for (Map.Entry<String, BlockStateUpgradeSchemaModelFlattenInfo> entry : model.flattenedProperties.entrySet()) {
            result.flattenedProperties.put(entry.getKey(), jsonModelToFlattenRule(entry.getValue()));
        }

        // Remapped States
        for (Map.Entry<String, List<BlockStateUpgradeSchemaBlockRemap>> entry : model.remappedStates.entrySet()) {
            String oldBlockName = entry.getKey();
            for (BlockStateUpgradeSchemaBlockRemap remap : entry.getValue()) {
                String remapName = remap.newName != null ? remap.newName : jsonModelToFlattenRule(remap.newFlattenedName).toString();
                result.remappedStates.putIfAbsent(oldBlockName, new ArrayList<>());
                result.remappedStates.get(oldBlockName).add(new BlockStateUpgradeSchemaBlockRemap(
                        remap.oldState.stream().map(BlockStateUpgradeSchemaModelTag::toString).collect(Collectors.toList()),
                        remapName,
                        remap.newState.stream().map(BlockStateUpgradeSchemaModelTag::toString).collect(Collectors.toList()),
                        remap.copiedState != null ? remap.copiedState : new ArrayList<>()
                ));
            }
        }

        return result;
    }

    // Helper functions to convert model tags and flatten rules
    private static Object jsonModelToTag(Object model) {
        // Implement tag conversion logic based on your Tag class
        return model;
    }

    private static BlockStateUpgradeSchemaModelFlattenInfo jsonModelToFlattenRule(BlockStateUpgradeSchemaModelFlattenInfo flattenRule) {
        // Implement conversion logic
        return flattenRule;
    }

    // Method to serialize schema into JSON model
    public static BlockStateUpgradeSchemaModel toJsonModel(BlockStateUpgradeSchema schema) {
        BlockStateUpgradeSchemaModel result = new BlockStateUpgradeSchemaModel();
        result.maxVersionMajor = schema.maxVersionMajor;
        result.maxVersionMinor = schema.maxVersionMinor;
        result.maxVersionPatch = schema.maxVersionPatch;
        result.maxVersionRevision = schema.maxVersionRevision;

        result.renamedIds = schema.renamedIds;
        result.renamedProperties = schema.renamedProperties;
        result.removedProperties = schema.removedProperties;

        // Adding Added Properties to JSON model
        for (Map.Entry<String, var> entry : schema.addedProperties.entrySet()) {
            result.addedProperties.put(entry.getKey(), entry.getValue());
        }

        // Build remapped values index and flatten rules
        buildRemappedValuesIndex(schema, result);

        return result;
    }

}

