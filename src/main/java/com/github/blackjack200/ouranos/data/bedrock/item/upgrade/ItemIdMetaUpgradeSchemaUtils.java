package com.github.blackjack200.ouranos.data.bedrock.item.upgrade;

import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.model.ItemIdMetaUpgradeSchemaModel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemIdMetaUpgradeSchemaUtils {

    private static final Gson gson = new Gson();

    /**
     * Loads schemas from files in the specified directory up to the specified max schema ID.
     *
     * @param path       The path to the directory containing the schema files.
     * @param maxSchemaId The maximum schema ID to load.
     * @return A map of schema IDs to schemas.
     * @throws IOException If an I/O error occurs.
     */
    public static Map<Integer, ItemIdMetaUpgradeSchema> loadSchemas(String path, int maxSchemaId) throws IOException {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("The specified path is not a valid directory: " + path);
        }

        Map<Integer, ItemIdMetaUpgradeSchema> result = new TreeMap<>();

        // Iterate over files in the directory that match the schema ID pattern
        File[] files = dir.listFiles((dir1, name) -> name.matches("^\\d{4}.*\\.json$"));
        if (files == null) {
            throw new IOException("Error reading the directory contents: " + path);
        }

        for (File file : files) {
            String filename = file.getName();
            Pattern pattern = Pattern.compile("^(\\d{4}).*\\.json$");
            Matcher matcher = pattern.matcher(filename);

            if (matcher.find()) {
                int schemaId = Integer.parseInt(matcher.group(1));
                if (schemaId > maxSchemaId) {
                    continue;
                }

                try (FileReader reader = new FileReader(file)) {
                    // Read the file content
                    JsonElement jsonElement = gson.fromJson(reader, JsonElement.class);
                    ItemIdMetaUpgradeSchema schema = loadSchemaFromJson(jsonElement, schemaId);
                    result.put(schemaId, schema);
                } catch (JsonSyntaxException | IOException e) {
                    throw new IOException("Loading schema file " + file.getPath() + ": " + e.getMessage(), e);
                }
            }
        }

        return result;
    }

    /**
     * Loads a schema from the given JSON element.
     *
     * @param jsonElement The JSON element to parse.
     * @param schemaId The schema ID.
     * @return The parsed schema.
     */
    public static ItemIdMetaUpgradeSchema loadSchemaFromJson(JsonElement jsonElement, int schemaId) {
        if (!jsonElement.isJsonObject()) {
            throw new RuntimeException("Unexpected root type of schema file, expected object");
        }

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        ItemIdMetaUpgradeSchemaModel model = gson.fromJson(jsonObject, ItemIdMetaUpgradeSchemaModel.class);

        return new ItemIdMetaUpgradeSchema(model.getRenamedIds(), model.getRemappedMetas(), schemaId);
    }
}
