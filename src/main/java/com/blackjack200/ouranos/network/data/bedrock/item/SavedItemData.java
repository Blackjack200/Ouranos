package com.blackjack200.ouranos.network.data.bedrock.item;

import com.blackjack200.ouranos.network.data.bedrock.block.BlockStateData;
import org.cloudburstmc.nbt.NbtMap;

public final class SavedItemData {

    public static final String TAG_NAME = "Name";
    public static final String TAG_DAMAGE = "Damage";
    public static final String TAG_BLOCK = "Block";
    public static final String TAG_TAG = "tag";

    private final String name;
    private final int meta;
    private final BlockStateData block;
    private final NbtMap tag;

    // Constructor
    public SavedItemData(String name, int meta, BlockStateData block, NbtMap tag) {
        this.name = name;
        this.meta = meta;
        this.block = block;
        this.tag = tag;
    }

    // Getters
    public String getName() {
        return name;
    }

    public int getMeta() {
        return meta;
    }

    public BlockStateData getBlock() {
        return block;
    }

    public NbtMap getTag() {
        return tag;
    }

    // Convert to NBT
    public NbtMap toNbt() {
        var result = NbtMap.builder();
        result.putString(TAG_NAME, this.name);
        result.putShort(TAG_DAMAGE, (short) this.meta);

        if (this.block != null) {
            result.put(TAG_BLOCK, this.block.toNbt());
        }
        if (this.tag != null) {
            result.put(TAG_TAG, this.tag);
        }
        result.putLong(VersionInfo.TAG_WORLD_DATA_VERSION, VersionInfo.WORLD_DATA_VERSION);

        return result;
    }
}
