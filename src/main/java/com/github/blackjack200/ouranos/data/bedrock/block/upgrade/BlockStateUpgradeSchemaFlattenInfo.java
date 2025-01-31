package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import lombok.Getter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BlockStateUpgradeSchemaFlattenInfo {

    @Getter
    private final String prefix;
    @Getter
    private final String flattenedProperty;
    @Getter
    private final String suffix;
    @Getter
    private final Map<String, String> flattenedValueRemaps;
    private final NbtType<?> flattenedPropertyType;

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
            NbtType<?> flattenedPropertyType
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
                (Objects.equals(this.flattenedPropertyType, that.flattenedPropertyType));
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
