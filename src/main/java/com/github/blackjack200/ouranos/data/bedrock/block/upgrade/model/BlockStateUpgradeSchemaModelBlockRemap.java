package com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelBlockRemap {
    public Map<String, BlockStateUpgradeSchemaModelTag> oldState;
    public String newName;
    public BlockStateUpgradeSchemaModelFlattenInfo newFlattenedName;
    public Map<String, BlockStateUpgradeSchemaModelTag> newState;
    public List<String> copiedState;

    public BlockStateUpgradeSchemaModelBlockRemap(Map<String,BlockStateUpgradeSchemaModelTag> oldState,
                                                  Object newNameRule,
                                                  Map<String,BlockStateUpgradeSchemaModelTag> newState,
                                                  List<String> copiedState) {
        this.oldState = (oldState.isEmpty()) ? null : oldState;

        if (newNameRule instanceof BlockStateUpgradeSchemaModelFlattenInfo) {
            this.newFlattenedName = (BlockStateUpgradeSchemaModelFlattenInfo) newNameRule;
        } else {
            this.newName = (String) newNameRule;
        }

        this.newState = (newState.isEmpty()) ? null : newState;
        this.copiedState = copiedState;
    }

}
