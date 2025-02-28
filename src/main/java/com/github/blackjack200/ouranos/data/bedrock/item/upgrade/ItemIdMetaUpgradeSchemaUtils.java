package com.github.blackjack200.ouranos.data.bedrock.item.upgrade;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.data.bedrock.item.upgrade.model.ItemIdMetaUpgradeSchemaModel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import lombok.experimental.UtilityClass;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

@UtilityClass
public class ItemIdMetaUpgradeSchemaUtils {
    private static final Gson GSON = new Gson();
    private static final String JSON_FILE_PATTERN = "^(\\d{4}).*\\.json$";
    private static final String JAR_PATH_PREFIX = "file:";
    private static final String JAR_PATH_SUFFIX = ".jar!";

    public static Map<Integer, ItemIdMetaUpgradeSchema> loadSchemas(String path, int maxSchemaId) throws IOException {
        var result = new TreeMap<Integer, ItemIdMetaUpgradeSchema>();
        var resourceUrl = Ouranos.class.getClassLoader().getResource(path);

        if (resourceUrl == null) {
            throw new IOException("The specified path does not exist: " + path);
        }

        var resourcePath = resourceUrl.getPath();
        if (resourcePath.contains(JAR_PATH_SUFFIX)) {
            loadSchemasFromJar(resourcePath, path, maxSchemaId, result);
        } else {
            loadSchemasFromFileSystem(resourcePath, maxSchemaId, result);
        }

        return result;
    }

    private static void loadSchemasFromJar(String resourcePath, String path, int maxSchemaId, Map<Integer, ItemIdMetaUpgradeSchema> result) throws IOException {
        var jarPath = resourcePath.substring(JAR_PATH_PREFIX.length(), resourcePath.indexOf(JAR_PATH_SUFFIX) + 4);
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);

        try (var jarFile = new JarFile(jarPath)) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith(path) && entry.getName().matches(".*\\d{4}.*\\.json$")) {
                    processSchemaEntry(jarFile, entry, maxSchemaId, result);
                }
            }
        }
    }

    private static void loadSchemasFromFileSystem(String resourcePath, int maxSchemaId, Map<Integer, ItemIdMetaUpgradeSchema> result) throws IOException {
        var dir = new File(URLDecoder.decode(resourcePath, StandardCharsets.UTF_8));
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IOException("The specified path is not a valid directory: " + resourcePath);
        }

        var files = dir.listFiles((dir1, name) -> name.matches(JSON_FILE_PATTERN));
        if (files == null) {
            throw new IOException("Error reading the directory contents: " + resourcePath);
        }

        for (var file : files) {
            processSchemaFile(file, maxSchemaId, result);
        }
    }

    private static void processSchemaEntry(JarFile jarFile, JarEntry entry, int maxSchemaId, Map<Integer, ItemIdMetaUpgradeSchema> result) throws IOException {
        var pattern = Pattern.compile(".*/(\\d{4}).*\\.json$");
        var matcher = pattern.matcher(entry.getName());
        if (matcher.find()) {
            int schemaId = Integer.parseInt(matcher.group(1));
            if (schemaId <= maxSchemaId) {
                try (InputStream inputStream = jarFile.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(inputStream)) {
                    loadSchemaFromStream(reader, schemaId, result);
                }
            }
        }
    }

    private static void processSchemaFile(File file, int maxSchemaId, Map<Integer, ItemIdMetaUpgradeSchema> result) throws IOException {
        var filename = file.getName();
        var pattern = Pattern.compile(JSON_FILE_PATTERN);
        var matcher = pattern.matcher(filename);

        if (matcher.find()) {
            var schemaId = Integer.parseInt(matcher.group(1));
            if (schemaId <= maxSchemaId) {
                try (FileReader reader = new FileReader(file)) {
                    loadSchemaFromStream(reader, schemaId, result);
                }
            }
        }
    }

    private static void loadSchemaFromStream(Reader reader, int schemaId, Map<Integer, ItemIdMetaUpgradeSchema> result) throws IOException {
        try {
            var jsonElement = GSON.fromJson(reader, JsonElement.class);
            var schema = loadSchemaFromJson(jsonElement, schemaId);
            result.put(schemaId, schema);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse schema file: " + e.getMessage(), e);
        }
    }

    public static ItemIdMetaUpgradeSchema loadSchemaFromJson(JsonElement jsonElement, int schemaId) {
        if (!jsonElement.isJsonObject()) {
            throw new RuntimeException("Unexpected root type of schema file, expected object");
        }

        var jsonObject = jsonElement.getAsJsonObject();
        var model = GSON.fromJson(jsonObject, ItemIdMetaUpgradeSchemaModel.class);

        return new ItemIdMetaUpgradeSchema(model.getRenamedIds(), model.getRemappedMetas(), schemaId);
    }
}