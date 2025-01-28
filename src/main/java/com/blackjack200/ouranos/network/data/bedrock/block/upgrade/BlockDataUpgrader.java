package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import com.blackjack200.ouranos.network.data.bedrock.block.BlockStateData;
import com.blackjack200.ouranos.network.data.bedrock.item.upgrade.BlockStateDeserializeException;

public class BlockDataUpgrader {
    private BlockIdMetaUpgrader blockIdMetaUpgrader;
    private BlockStateUpgrader blockStateUpgrader;

    public BlockDataUpgrader(BlockIdMetaUpgrader blockIdMetaUpgrader, BlockStateUpgrader blockStateUpgrader) {
        this.blockIdMetaUpgrader = blockIdMetaUpgrader;
        this.blockStateUpgrader = blockStateUpgrader;
    }

    /**
     * @throws BlockStateDeserializeException
     */
    public BlockStateData upgradeStringIdMeta(String id, int meta) throws BlockStateDeserializeException {
        return blockIdMetaUpgrader.fromStringIdMeta(id, meta);
    }

    /**
     * @throws BlockStateDeserializeException
     */
    public BlockStateData upgradeBlockStateNbt(org.cloudburstmc.nbt.NbtMap tag) throws BlockStateDeserializeException {
        BlockStateData blockStateData;
        if (tag.getString("name") != null && tag.get("val") != null) {
            // Legacy (pre-1.13) blockstate - upgrade it to a version we understand
            String id = tag.getString("name");
            short data = tag.getShort("val");

            blockStateData = upgradeStringIdMeta(id, data);
        } else {
            // Modern (post-1.13) blockstate
            blockStateData = BlockStateData.fromNbt(tag);
        }

        return blockStateUpgrader.upgrade(blockStateData);
    }

    public BlockStateUpgrader getBlockStateUpgrader() {
        return blockStateUpgrader;
    }

    public BlockIdMetaUpgrader getBlockIdMetaUpgrader() {
        return blockIdMetaUpgrader;
    }
}
