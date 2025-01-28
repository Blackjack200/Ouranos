package com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelBlockRemap {
    public BlockStateUpgradeSchemaModelTag[] oldState;
    public String newName;
    public BlockStateUpgradeSchemaModelFlattenInfo newFlattenedName;
    public BlockStateUpgradeSchemaModelTag[] newState;
    public List<String> copiedState;

    public BlockStateUpgradeSchemaModelBlockRemap(BlockStateUpgradeSchemaModelTag[] oldState,
                                                  Object newNameRule,
                                                  BlockStateUpgradeSchemaModelTag[] newState,
                                                  List<String> copiedState) {
        this.oldState = (oldState.length == 0) ? null : oldState;

        if (newNameRule instanceof BlockStateUpgradeSchemaModelFlattenInfo) {
            this.newFlattenedName = (BlockStateUpgradeSchemaModelFlattenInfo) newNameRule;
        } else {
            this.newName = (String) newNameRule;
        }

        this.newState = (newState.length == 0) ? null : newState;
        this.copiedState = copiedState;
    }

}
