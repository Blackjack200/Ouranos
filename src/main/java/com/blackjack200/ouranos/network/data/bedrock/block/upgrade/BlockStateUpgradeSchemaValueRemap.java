package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

public final class BlockStateUpgradeSchemaValueRemap {

    private final Nbt oldTag;
    private final Tag newTag;

    public BlockStateUpgradeSchemaValueRemap(Tag oldTag, Tag newTag) {
        this.oldTag = oldTag;
        this.newTag = newTag;
    }

    public Tag getOldTag() {
        return oldTag;
    }

    public Tag getNewTag() {
        return newTag;
    }
}

