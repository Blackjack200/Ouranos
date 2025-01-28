package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BlockStateUpgradeSchemaFlattenInfo {

    private final String prefix;
    private final String flattenedProperty;
    private final String suffix;
    private final Map<String, String> flattenedValueRemaps;
    private final String flattenedPropertyType;

    /**
     * Constructor.
     *
     * @param flattenedValueRemaps  A map where the key is a string and the value is a string.
     * @param flattenedPropertyType The flattened property type, can be null.
     */
    public BlockStateUpgradeSchemaFlattenInfo(
            String prefix,
            String flattenedProperty,
            String suffix,
            Map<String, String> flattenedValueRemaps,
            String flattenedPropertyType
    ) {
        this.prefix = prefix;
        this.flattenedProperty = flattenedProperty;
        this.suffix = suffix;
        // Sort the map by keys (sorting maps can be achieved by using TreeMap in Java)
        this.flattenedValueRemaps = flattenedValueRemaps.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        this.flattenedPropertyType = flattenedPropertyType;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getFlattenedProperty() {
        return flattenedProperty;
    }

    public String getSuffix() {
        return suffix;
    }

    public Map<String, String> getFlattenedValueRemaps() {
        return flattenedValueRemaps;
    }

    public String getFlattenedPropertyType() {
        return flattenedPropertyType;
    }

    /**
     * Compares two BlockStateUpgradeSchemaFlattenInfo objects for equality.
     *
     * @param that Another BlockStateUpgradeSchemaFlattenInfo to compare with.
     * @return true if both objects are equal, false otherwise.
     */
    public boolean equals(BlockStateUpgradeSchemaFlattenInfo that) {
        if (that == null) {
            return false;
        }
        return this.prefix.equals(that.prefix) &&
                this.flattenedProperty.equals(that.flattenedProperty) &&
                this.suffix.equals(that.suffix) &&
                this.flattenedValueRemaps.equals(that.flattenedValueRemaps) &&
                (this.flattenedPropertyType == null ? that.flattenedPropertyType == null :
                        this.flattenedPropertyType.equals(that.flattenedPropertyType));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockStateUpgradeSchemaFlattenInfo that = (BlockStateUpgradeSchemaFlattenInfo) obj;
        return equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, flattenedProperty, suffix, flattenedValueRemaps, flattenedPropertyType);
    }
}
