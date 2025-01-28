package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import org.cloudburstmc.nbt.NbtMap;

public final class BlockStateUpgradeSchemaValueRemap {

    private final NbtMap oldTag;
    private final NbtMap newTag;

    public BlockStateUpgradeSchemaValueRemap(NbtMap oldTag, NbtMap newTag) {
        this.oldTag = oldTag;
        this.newTag = newTag;
    }

    public NbtMap getOldTag() {
        return oldTag;
    }

    public NbtMap getNewTag() {
        return newTag;
    }
}

