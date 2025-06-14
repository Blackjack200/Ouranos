package com.github.blackjack200.ouranos.data.bedrock.block;

import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import com.github.blackjack200.ouranos.utils.BinaryStream;
import com.github.blackjack200.ouranos.utils.HashUtils;
import com.github.blackjack200.ouranos.utils.VarInt;
import lombok.SneakyThrows;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.util.HashMap;
import java.util.Map;

public class BlockIdMetaUpgrader {
    private Map<Integer, Block> mappingTable;

    public BlockIdMetaUpgrader(Map<Integer, Block> mappingTable) {
        this.mappingTable = mappingTable;
    }

    public Block fromLatestStateHash(int hash) {
        return this.mappingTable.get(hash);
    }

    public record Block(String id, long meta) {

    }

    @SneakyThrows
    public static BlockIdMetaUpgrader loadFromString(BinaryStream data) {
        Map<Integer, Block> mappingTable = new HashMap<>();
        var len = VarInt.readUnsignedVarInt(data);
        for (int i = 0; i < len; i++) {
            var legacyStringId = data.getString();
            var pairs = VarInt.readUnsignedVarInt(data);
            for (int j = 0; j < pairs; j++) {
                var meta = VarInt.readUnsignedVarInt(data);
                try (var reader = NbtUtils.createReaderLE(data)) {
                    var rawState = (NbtMap) reader.readTag();
                    var state = HashUtils.computeBlockStateHash(BlockStateDictionary.hackedUpgradeBlockState(rawState, BlockStateUpdaters.LATEST_VERSION));
                    mappingTable.put(state, new Block(legacyStringId, meta));
                }
            }

        }
        return new BlockIdMetaUpgrader(mappingTable);
    }
}