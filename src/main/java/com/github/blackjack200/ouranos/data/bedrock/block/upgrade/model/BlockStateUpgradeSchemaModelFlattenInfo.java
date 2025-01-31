package com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelFlattenInfo {
    public String prefix;
    public String flattenedProperty;
    public String flattenedPropertyType;
    public String suffix;
    public Map<String, String> flattenedValueRemaps;

    public BlockStateUpgradeSchemaModelFlattenInfo(String prefix, String flattenedProperty,
                                                   String suffix, Map<String, String> flattenedValueRemaps,
                                                   String flattenedPropertyType) {
        this.prefix = prefix;
        this.flattenedProperty = flattenedProperty;
        this.suffix = suffix;
        this.flattenedValueRemaps = flattenedValueRemaps;
        this.flattenedPropertyType = flattenedPropertyType;
    }

}
