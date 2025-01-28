package com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelValueRemap {
    public BlockStateUpgradeSchemaModelTag old;
    @SerializedName("new")
    public BlockStateUpgradeSchemaModelTag newTag;

    // Constructor
    public BlockStateUpgradeSchemaModelValueRemap(BlockStateUpgradeSchemaModelTag oldTag, BlockStateUpgradeSchemaModelTag newTag) {
        this.old = oldTag;
        this.newTag = newTag;
    }
}

