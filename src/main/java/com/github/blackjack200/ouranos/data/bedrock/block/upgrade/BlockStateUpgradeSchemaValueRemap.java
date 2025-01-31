package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import org.cloudburstmc.nbt.NbtMap;

public final class BlockStateUpgradeSchemaValueRemap {

    private final Object oldTag;
    private final Object newTag;

    public BlockStateUpgradeSchemaValueRemap(Object oldTag, Object newTag) {
        this.oldTag = oldTag;
        this.newTag = newTag;
    }

    public Object getOldTag() {
        return oldTag;
    }

    public Object getNewTag() {
        return newTag;
    }
}

