package com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelValueRemap {
    public BlockStateUpgradeSchemaModelTag oldTag;
    @SerializedName("new")
    public BlockStateUpgradeSchemaModelTag newTag;

    // Constructor
    public BlockStateUpgradeSchemaModelValueRemap(BlockStateUpgradeSchemaModelTag oldTag, BlockStateUpgradeSchemaModelTag newTag) {
        this.oldTag = oldTag;
        this.newTag = newTag;
    }
}

