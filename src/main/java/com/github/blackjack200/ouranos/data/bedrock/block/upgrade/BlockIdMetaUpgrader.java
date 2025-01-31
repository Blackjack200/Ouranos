package com.github.blackjack200.ouranos.data.bedrock.block.upgrade;

import com.github.blackjack200.ouranos.data.bedrock.block.BlockStateData;
import com.github.blackjack200.ouranos.data.bedrock.block.BlockStateDeserializeException;
import com.github.blackjack200.ouranos.utils.BinaryStream;
import com.github.blackjack200.ouranos.utils.VarInt;
import lombok.SneakyThrows;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.util.HashMap;
import java.util.Map;

public class BlockIdMetaUpgrader {
    private Map<String, Map<Integer, BlockStateData>> mappingTable;
    private SchemaLegacyBlockIdToStringIdMap legacyNumericIdMap;

    public BlockIdMetaUpgrader(Map<String, Map<Integer, BlockStateData>> mappingTable, SchemaLegacyBlockIdToStringIdMap legacyNumericIdMap) {
        this.mappingTable = mappingTable;
        this.legacyNumericIdMap = legacyNumericIdMap;
    }

    /**
     * @throws BlockStateDeserializeException
     */
    public BlockStateData fromStringIdMeta(String id, int meta) throws BlockStateDeserializeException {
        Map<Integer, BlockStateData> metaMap = mappingTable.get(id);
        if (metaMap == null) {
            throw new BlockStateDeserializeException("Unknown legacy block string ID " + id);
        }
        BlockStateData stateData = metaMap.get(meta);
        if (stateData != null) {
            return stateData;
        }
        stateData = metaMap.get(0); // Default to meta 0 if not found
        if (stateData == null) {
            throw new BlockStateDeserializeException("Unknown legacy block string ID " + id);
        }
        return stateData;
    }

    /**
     * @throws BlockStateDeserializeException
     */
    public BlockStateData fromIntIdMeta(int id, int meta) throws BlockStateDeserializeException {
        String stringId = legacyNumericIdMap.fromNumeric(id);
        if (stringId == null) {
            throw new BlockStateDeserializeException("Unknown legacy block numeric ID " + id);
        }
        return fromStringIdMeta(stringId, meta);
    }

    /**
     * Adds a mapping of legacy block numeric ID to modern string ID. This is used for upgrading blocks from pre-1.2.13
     * worlds (PM3). It's also needed for upgrading flower pot contents and falling blocks from PM4 worlds.
     */
    public void addIntIdToStringIdMapping(int intId, String stringId) {
        legacyNumericIdMap.add(stringId, intId);
    }

    /**
     * Adds a mapping of legacy block ID and meta to modern blockstate data. This may be needed for upgrading data from
     * stored custom blocks from older versions of PocketMine-MP.
     */
    public void addIdMetaToStateMapping(String stringId, int meta, BlockStateData stateData) {
        if (mappingTable.containsKey(stringId) && mappingTable.get(stringId).containsKey(meta)) {
            throw new IllegalArgumentException("A mapping for " + stringId + ":" + meta + " already exists");
        }
        mappingTable.computeIfAbsent(stringId, k -> new HashMap<>()).put(meta, stateData);
    }

    @SneakyThrows
    public static BlockIdMetaUpgrader loadFromString(String data, SchemaLegacyBlockIdToStringIdMap idMap, BlockStateUpgrader blockStateUpgrader) {
        Map<String, Map<Integer, BlockStateData>> mappingTable = new HashMap<>();
        BinaryStream legacyStateMapReader = new BinaryStream(data.getBytes());
        var nbtReader = NbtUtils.createReaderLE(legacyStateMapReader);

        var idCount = legacyStateMapReader.getUnsignedVarInt();
        for (var idIndex = 0; idIndex < idCount && !legacyStateMapReader.feof(); idIndex++) {
            String id = legacyStateMapReader.getString();

            var metaCount = legacyStateMapReader.getUnsignedVarInt();
            for (int metaIndex = 0; metaIndex < metaCount; metaIndex++) {
                var meta = legacyStateMapReader.getUnsignedVarInt();

                NbtMap state = (NbtMap) nbtReader.readTag();
                mappingTable.computeIfAbsent(id, k -> new HashMap<>());
                mappingTable.get(id).put((int) meta, blockStateUpgrader.upgrade(BlockStateData.fromNbt(state)));
            }
        }

        if (!legacyStateMapReader.feof()) {
            throw new RuntimeException("Unexpected trailing data in legacy state map data");
        }

        return new BlockIdMetaUpgrader(mappingTable, idMap);
    }
}
