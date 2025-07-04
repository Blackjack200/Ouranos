package com.github.blackjack200.ouranos.data.bedrock;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.data.bedrock.block.BlockIdMetaUpgrader;
import com.github.blackjack200.ouranos.utils.BinaryStream;
import lombok.SneakyThrows;

public class GlobalBlockDataHandlers {
    private static BlockIdMetaUpgrader blockIdMetaUpgrader = null;


    @SneakyThrows
    public static BlockIdMetaUpgrader getUpgrader() {
        if (blockIdMetaUpgrader == null) {
            try (var bin = Ouranos.class.getClassLoader().getResourceAsStream("block_schema/id_meta_to_nbt/1.12.0.bin")) {
                assert bin != null;
                blockIdMetaUpgrader = BlockIdMetaUpgrader.loadFromString(new BinaryStream(bin.readAllBytes()));
            }
        }
        return blockIdMetaUpgrader;
    }
}
