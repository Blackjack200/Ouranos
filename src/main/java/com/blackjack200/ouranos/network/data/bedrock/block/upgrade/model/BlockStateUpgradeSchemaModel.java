package com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.List;

@Setter
@Getter
public class BlockStateUpgradeSchemaModel {
    public int maxVersionMajor;
    public int maxVersionMinor;
    public int maxVersionPatch;
    public int maxVersionRevision;

    public Map<String, String> renamedIds;

    public Map<String, Map<String, BlockStateUpgradeSchemaModelTag>> addedProperties;

    public Map<String, List<String>> removedProperties;

    public Map<String, Map<String, String>> renamedProperties;

    public Map<String, Map<String, String>> remappedPropertyValues;

    public Map<String, List<BlockStateUpgradeSchemaModelValueRemap>> remappedPropertyValuesIndex;

    public Map<String, BlockStateUpgradeSchemaModelFlattenInfo> flattenedProperties;

    public Map<String, List<BlockStateUpgradeSchemaModelBlockRemap>> remappedStates;

}

