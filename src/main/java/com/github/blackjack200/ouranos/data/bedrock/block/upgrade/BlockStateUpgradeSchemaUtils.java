package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model.BlockStateUpgradeSchemaModel;
import com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model.BlockStateUpgradeSchemaModelFlattenInfo;
import com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model.BlockStateUpgradeSchemaModelTag;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public final class BlockStateUpgradeSchemaUtils {

    private static final Gson gson = new Gson();

    public static String describe(BlockStateUpgradeSchema schema) {
        List<String> lines = new ArrayList<>();

        lines.add("Renames:");
        for (var rename : schema.renamedIds.values()) {
            lines.add("- " + rename);
        }

        lines.add("Added properties:");
        for (var entry : schema.addedProperties.entrySet()) {
            String blockName = entry.getKey();
            for (var tagEntry : entry.getValue().entrySet()) {
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
        for (var entry : schema.renamedProperties.entrySet()) {
            String blockName = entry.getKey();
            for (Map.Entry<String, String> tagEntry : entry.getValue().entrySet()) {
                lines.add("- " + blockName + " has " + tagEntry.getKey() + " renamed to " + tagEntry.getValue());
            }
        }

        lines.add("Remapped property values:");
        for (Map.Entry<String, Map<String, List<BlockStateUpgradeSchemaValueRemap>>> entry : schema.remappedPropertyValues.entrySet()) {
            String blockName = entry.getKey();
            for (var remapEntry : entry.getValue().entrySet()) {
                for (BlockStateUpgradeSchemaValueRemap remap : remapEntry.getValue()) {
                    lines.add("- " + blockName + " has " + remapEntry.getKey() + " value changed from " + remap.getOldTag() + " to " + remap.getNewTag());
                }
            }
        }

        return String.join("\n", lines);
    }

    public static BlockStateUpgradeSchemaModelTag tagToJsonModel(Object t) {
        BlockStateUpgradeSchemaModelTag model = new BlockStateUpgradeSchemaModelTag();
        if (t instanceof Integer tag) {
            model.setIntValue(tag);
        } else if (t instanceof String tag) {
            model.setStringValue(tag);
        } else if (t instanceof Byte tag) {
            model.setByteValue((tag));
        } else {
            throw new IllegalArgumentException("Unexpected value type " + t.getClass().getName());
        }

        return model;
    }

    // Helper functions to convert model tags and flatten rules
    private static Object jsonModelToTag(BlockStateUpgradeSchemaModelTag model) {
        if(model == null){
            return null;
        }
        if (model.byteValue != null && model.intValue == null && model.stringValue == null) {
            return model.byteValue;
        } else if (model.byteValue == null && model.intValue != null && model.stringValue == null) {
            return model.intValue;
        } else if (model.byteValue == null && model.intValue == null && model.stringValue != null) {
            return model.stringValue;
        } else {
            throw new RuntimeException("Malformed JSON model tag, expected exactly one of 'byte', 'int' or 'string' properties");
        }
    }

    // Static method to parse the schema from JSON
    public static BlockStateUpgradeSchema fromJsonModel(BlockStateUpgradeSchemaModel model, int schemaId) {
        BlockStateUpgradeSchema result = new BlockStateUpgradeSchema(
                model.maxVersionMajor,
                model.maxVersionMinor,
                model.maxVersionPatch,
                model.maxVersionRevision,
                schemaId
        );

        result.renamedIds = model.renamedIds != null ? model.renamedIds : new HashMap<>();
        result.renamedProperties = model.renamedProperties != null ? model.renamedProperties : new HashMap<>();
        result.removedProperties = model.removedProperties != null ? model.removedProperties : new HashMap<>();

        // Processing addedProperties
        if (model.addedProperties != null) {
            for (var entry : (model.addedProperties).entrySet()) {
                String blockName = entry.getKey();
                result.addedProperties.putIfAbsent(blockName, NbtMap.builder().build());
                var v = result.addedProperties.get(blockName).toBuilder();

                entry.getValue().forEach((propertyName, propertyValue) -> {
                    v.put(propertyName, jsonModelToTag(propertyValue));
                });

                result.addedProperties.put(blockName, v.build());
            }
        }

        // Processing remappedPropertyValuesIndex
        Map<String, List<BlockStateUpgradeSchemaValueRemap>> convertedRemappedValuesIndex = new HashMap<>();
        if (model.remappedPropertyValuesIndex != null) {
            for (var entry : (model.remappedPropertyValuesIndex).entrySet()) {
                var mappingKey = entry.getKey();
                var mappingValues = entry.getValue();
                for (var oldNew : mappingValues) {
                    convertedRemappedValuesIndex
                            .computeIfAbsent(mappingKey, k -> new ArrayList<>())
                            .add(new BlockStateUpgradeSchemaValueRemap(
                                    jsonModelToTag(oldNew.oldTag),
                                    jsonModelToTag(oldNew.newTag)
                            ));
                }
            }
        }

        // Processing remappedPropertyValues
        if (model.remappedPropertyValues != null) {
            model.remappedPropertyValues.forEach((blockName, properties) -> {
                properties.forEach((property, mappedValuesKey) -> {
                    List<BlockStateUpgradeSchemaValueRemap> remappedValues = convertedRemappedValuesIndex.get(mappedValuesKey);
                    if (remappedValues == null) {
                        throw new RuntimeException("Missing key from schema values index " + mappedValuesKey);
                    }
                    result.remappedPropertyValues
                            .computeIfAbsent(blockName, k -> new HashMap<>())
                            .put(property, remappedValues);
                });
            });
        }

        // Processing flattenedProperties
        if (model.flattenedProperties != null) {
            model.flattenedProperties.forEach((blockName, flattenRule) -> {
                result.flattenedProperties.put(blockName, jsonModelToFlattenRule(flattenRule));
            });
        }

        // Processing remappedStates
        if (model.remappedStates != null) {
            for (var entry : (model.remappedStates).entrySet()) {
                var oldBlockName = entry.getKey();
                var remaps = entry.getValue();
                for (var remap : remaps) {
                    Object remapName;
                    if (remap.newName != null) {
                        remapName = remap.newName;
                    } else if (remap.newFlattenedName != null) {
                        var flattenRule = remap.newFlattenedName;
                        remapName = jsonModelToFlattenRule(flattenRule);
                    } else {
                        throw new RuntimeException("Expected exactly one of 'newName' or 'newFlattenedName' properties to be set");
                    }

                    Map<String, Object> oldState = new HashMap<>();
                    Map<String, Object> newState = new HashMap<>();
                    if(remap.oldState != null && remap.newState != null) {
                        remap.oldState.forEach((k, v) -> oldState.put(k, jsonModelToTag(v)));
                        remap.newState.forEach((k, v) -> newState.put(k, jsonModelToTag(v)));

                        result.remappedStates.computeIfAbsent(oldBlockName, k -> new ArrayList<>())
                                .add(new BlockStateUpgradeSchemaBlockRemap(
                                        oldState,
                                        remapName,
                                        newState,
                                        remap.copiedState != null ? remap.copiedState : new ArrayList<>()
                                ));
                    }
                }
            }
        }

        return result;
    }

    private static BlockStateUpgradeSchemaFlattenInfo jsonModelToFlattenRule(BlockStateUpgradeSchemaModelFlattenInfo flattenRule) {
        NbtType tagType;

        if (flattenRule.flattenedPropertyType == null) {
            tagType = NbtType.STRING;
        } else {

            switch (flattenRule.flattenedPropertyType) {
                case "string":
                    tagType = NbtType.STRING;
                    break;
                case "int":
                    tagType = NbtType.INT;
                    break;
                case "byte":
                    tagType = NbtType.BYTE;
                    break;
                default:
                    throw new RuntimeException("Unexpected flattened property type " + flattenRule.flattenedPropertyType + ", expected 'string', 'int' or 'byte'");
            }
        }

        return new BlockStateUpgradeSchemaFlattenInfo(
                flattenRule.prefix,
                flattenRule.flattenedProperty,
                flattenRule.suffix,
                flattenRule.flattenedValueRemaps != null ? flattenRule.flattenedValueRemaps : new HashMap<>(),
                tagType
        );
    }


    /**
     * Returns a list of schemas ordered by schema ID. Oldest schemas appear first.
     *
     * @return List<BlockStateUpgradeSchema>
     */
    @SneakyThrows
    public static List<BlockStateUpgradeSchema> loadSchemas(String path, int maxSchemaId) {
        File folder = new File(path);
        File[] files = folder.listFiles((dir, name) -> name.matches("^\\d{4}.*\\.json$"));

        if (files == null) {
            return Collections.emptyList(); // Return empty list if no files are found
        }

        Map<Integer, BlockStateUpgradeSchema> result = new TreeMap<>();

        for (File file : files) {
            String filename = file.getName();
            int schemaId = Integer.parseInt(filename.substring(0, 4)); // Get the first 4 digits as schemaId

            if (schemaId > maxSchemaId) {
                continue;
            }

            String fullPath = file.getPath();
            String raw = new String(Files.readAllBytes(Paths.get(fullPath)));

            try {
                BlockStateUpgradeSchema schema = loadSchemaFromString(raw, schemaId);
                result.put(schemaId, schema);
            } catch (RuntimeException e) {
                throw new RuntimeException("Loading schema file " + fullPath + ": " + e.getMessage(), e);
            }
        }

        return new ArrayList<>(result.values()); // Convert the TreeMap to a list
    }

    /**
     * Loads schema from JSON string and schema ID
     *
     * @param raw      JSON raw string
     * @param schemaId Schema ID
     * @return BlockStateUpgradeSchema
     */
    public static BlockStateUpgradeSchema loadSchemaFromString(String raw, int schemaId) {
        Gson gson = new Gson();
        JsonElement jsonElement;

        try {
            jsonElement = gson.fromJson(raw, JsonElement.class);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON: " + e.getMessage(), e);
        }

        if (!jsonElement.isJsonObject()) {
            throw new RuntimeException("Unexpected root type of schema file, expected object");
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        // Map the JSON to BlockStateUpgradeSchemaModel using Gson
        BlockStateUpgradeSchemaModel model = gson.fromJson(jsonObject, BlockStateUpgradeSchemaModel.class);

        // Convert from BlockStateUpgradeSchemaModel to BlockStateUpgradeSchema
        return fromJsonModel(model, schemaId);
    }
}

