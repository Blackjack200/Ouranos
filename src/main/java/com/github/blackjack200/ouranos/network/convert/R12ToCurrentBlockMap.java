package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.utils.BinaryStream;
import com.github.blackjack200.ouranos.utils.HashUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class R12ToCurrentBlockMap extends AbstractMapping {
    public record LegacyBlock(String id, int meta) {

    }

    public static final class Entry {
        private final Int2ObjectRBTreeMap<LegacyBlock> latestToLegacy;
        private final Map<String, Integer> legacyToLatest;

        public Entry(Int2ObjectRBTreeMap<LegacyBlock> latestToLegacy, Map<String, Integer> legacyToLatest) {
            this.latestToLegacy = latestToLegacy;
            this.legacyToLatest = legacyToLatest;
        }

        @SneakyThrows
        private static Entry load(int protocolId) {
            var reader = new BinaryStream(open(lookupAvailableFile("r12_to_current_block_map.bin", protocolId)).readAllBytes());
            var latestToLegacy = new Int2ObjectRBTreeMap<LegacyBlock>();
            var legacyToLatest = new HashMap<String, Integer>();
            while (!reader.feof()) {
                var id = reader.getString();
                var meta = reader.getLShort();
                var nbtReader = NbtUtils.createNetworkReader(reader);
                var state = (NbtMap) nbtReader.readTag();
                var latestState = BlockStateDictionary.hackedUpgradeBlockState(state, BlockStateUpdaters.LATEST_VERSION);
                log.info("{}:{} => {}", id, meta, latestState);
                var latestHash = HashUtils.computeBlockStateHash(latestState);
                latestToLegacy.put(latestHash, new LegacyBlock(id, meta));
                legacyToLatest.put(id + meta, latestHash);
            }
            reader.close();
            return new Entry(latestToLegacy, legacyToLatest);
        }
    }

    private static final Map<Integer, Entry> entries = new ConcurrentHashMap<>();

    public static Entry getInstance(int protocol) {
        return entries.computeIfAbsent(protocol, Entry::load);
    }

}
