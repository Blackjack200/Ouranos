package com.blackjack200.ouranos.network.convert;


import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.blackjack200.ouranos.utils.HashUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

@Log4j2
public class RuntimeBlockMapping extends AbstractMapping {
    public static class Entry {

        @Getter
        private final Map<Integer, NbtMap> bedrockKnownStates = new LinkedHashMap<>(15000);
        private final Map<Integer, Integer> hashToRuntimeId = new HashMap<>(15000);
        private final Map<Integer, Integer> runtimeIdToHash = new HashMap<>(15000);
        private Integer fallbackId;

        public Entry(int protocolId, URL url) {
            val states = new LinkedList<NbtMap>();
            try {
                InputStream reader = url.openStream();
                val nbtReader = NbtUtils.createNetworkReader(reader);
                while (reader.available() > 0) {
                    NbtMap blockState = (NbtMap) nbtReader.readTag();
                    var nbt = BlockStateUpdaters.updateBlockState(blockState, BlockStateUpdaters.LATEST_VERSION);
                    nbt = NbtMap.builder()
                            .putString("name", nbt.getString("name"))
                            .putCompound("states", nbt.getCompound("states"))
                            .build();
                    var hashCode = HashUtils.computeBlockStateHash(nbt.getString("name"), nbt);
                    this.bedrockKnownStates.put(hashCode, nbt);
                    states.add(nbt);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (int i = 0; i < states.size(); i++) {
                val state = states.get(i);
                val stateHash = HashUtils.computeBlockStateHash(state);
                this.hashToRuntimeId.put(stateHash, i);
                this.runtimeIdToHash.put(i, stateHash);
            }
            for (val v : this.bedrockKnownStates.entrySet()) {
                if (v.getValue().get("name").equals("minecraft:info_update")) {
                    this.fallbackId = Optional.of(this.toRuntimeId(v.getKey())).get();
                    break;
                }
            }
            if (this.fallbackId == null) {
                throw new RuntimeException("no fallback minecraft:info_update found.");
            }

            log.debug("Loaded runtime block mapping for protocol {}.", protocolId);
        }

        public Integer toRuntimeId(int hash) {
            return this.hashToRuntimeId.get(hash);
        }

        public Integer fromRuntimeId(int runtimeId) {
            return this.runtimeIdToHash.get(runtimeId);
        }

        public Integer fromNbt(String name, InputStream input) {
            try {
                var reader = NbtUtils.createReaderLE(input);
                var tg = (NbtMap) reader.readTag();
                return HashUtils.computeBlockStateHash(name, tg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int getFallback() {
            return this.fallbackId;
        }
    }

    private static RuntimeBlockMapping instance;

    public synchronized static void init() {
        if (instance == null) {
            instance = new RuntimeBlockMapping();
        }
    }

    private static final Map<Integer, Entry> mappings = new HashMap<>(ProtocolInfo.getPacketCodecs().size());
    private static final Map<Integer, URL> files = new HashMap<>(ProtocolInfo.getPacketCodecs().size());

    private RuntimeBlockMapping() {
        loadFile("canonical_block_states.nbt", files::put);
    }

    public static Entry getInstance(int protocolId) {
        init();
        if (!mappings.containsKey(protocolId)) {
            val entry = new Entry(protocolId, files.get(protocolId));
            synchronized (mappings) {
                mappings.put(protocolId, entry);
            }
        }
        return Objects.requireNonNull(mappings.get(protocolId), "Runtime block mapping for protocol " + protocolId + " not found.");
    }
}