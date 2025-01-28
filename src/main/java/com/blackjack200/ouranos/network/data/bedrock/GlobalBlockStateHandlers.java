package com.blackjack200.ouranos.network.data.bedrock;

import com.blackjack200.ouranos.network.data.bedrock.block.upgrade.*;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GlobalBlockStateHandlers {
    private static BlockDataUpgrader blockDataUpgrader = null;

    public static BlockDataUpgrader getUpgrader() {
        if (blockDataUpgrader == null) {
            BlockStateUpgrader blockStateUpgrader = new BlockStateUpgrader(
                    BlockStateUpgradeSchemaUtils.loadSchemas(
                            Path.of(BEDROCK_BLOCK_UPGRADE_SCHEMA_PATH, "nbt_upgrade_schema"),
                            Integer.MAX_VALUE
                    )
            );
            blockDataUpgrader = new BlockDataUpgrader(
                    BlockIdMetaUpgrader.loadFromString(
                            Files.readString(Path.of(BEDROCK_BLOCK_UPGRADE_SCHEMA_PATH, "id_meta_to_nbt/1.12.0.bin")),
                            LegacyBlockIdToStringIdMap.getInstance(),
                            blockStateUpgrader
                    ),
                    blockStateUpgrader
            );
        }
        return blockDataUpgrader;
    }
}
