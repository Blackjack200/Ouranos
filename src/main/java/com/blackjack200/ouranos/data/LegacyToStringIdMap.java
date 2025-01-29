package com.blackjack200.ouranos.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class LegacyToStringIdMap {
    /**
     * A map of legacy integer IDs to string IDs.
     */
    @Getter
    private final Map<Integer, String> legacyToString = new HashMap<>();

    protected LegacyToStringIdMap(URL file) {
        try (InputStreamReader reader = new InputStreamReader(file.openStream())) {
            Map<String, Integer> stringToLegacyId = new Gson().fromJson(reader, new TypeToken<Map<String, Integer>>() {
            }.getType());
            if (stringToLegacyId == null || stringToLegacyId.isEmpty()) {
                throw new IllegalArgumentException("Invalid format of ID map");
            }
            for (Map.Entry<String, Integer> entry : stringToLegacyId.entrySet()) {
                String stringId = entry.getKey();
                Integer legacyId = entry.getValue();
                if (stringId == null || legacyId == null) {
                    throw new IllegalArgumentException("ID map should have string keys and integer values");
                }
                this.legacyToString.put(legacyId, stringId);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ID map file", e);
        }
    }

    public String legacyToString(int legacy) {
        return this.legacyToString.get(legacy);
    }

    public void add(String string, int legacy) {
        if (this.legacyToString.containsKey(legacy)) {
            String existing = this.legacyToString.get(legacy);
            if (existing.equals(string)) {
                return; // The mapping already exists
            }
            throw new IllegalArgumentException("Legacy ID " + legacy + " is already mapped to string " + existing);
        }
        this.legacyToString.put(legacy, string);
    }
}
