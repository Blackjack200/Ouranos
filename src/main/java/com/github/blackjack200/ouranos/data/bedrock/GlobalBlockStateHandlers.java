package com.github.blackjack200.ouranos.data.bedrock;

import cn.hutool.core.io.FileUtil;
import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.data.bedrock.block.upgrade.*;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class GlobalBlockStateHandlers {
    private static BlockDataUpgrader blockDataUpgrader = null;

    public static BlockDataUpgrader getUpgrader() {
        if (blockDataUpgrader == null) {
            BlockStateUpgrader blockStateUpgrader = new BlockStateUpgrader(
                    BlockStateUpgradeSchemaUtils.loadSchemas(
                            Ouranos.class.getClassLoader().getResource("upgrade_block/nbt_upgrade_schema").getPath(),
                            Integer.MAX_VALUE
                    )
            );
            blockDataUpgrader = new BlockDataUpgrader(
                    BlockIdMetaUpgrader.loadFromString(
                            FileUtil.readString(Objects.requireNonNull(Ouranos.class.getClassLoader().getResource("upgrade_block/id_meta_to_nbt/1.12.0.bin")), StandardCharsets.UTF_8),
                            SchemaLegacyBlockIdToStringIdMap.getInstance(),
                            blockStateUpgrader
                    ),
                    blockStateUpgrader
            );
        }
        return blockDataUpgrader;
    }
}
