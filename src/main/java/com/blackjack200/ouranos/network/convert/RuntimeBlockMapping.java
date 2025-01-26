package com.blackjack200.ouranos.network.convert;


import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.blackjack200.ouranos.utils.BinaryStream;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

@Log4j2
public class RuntimeBlockMapping extends AbstractMapping {
    @Getter
    private static final RuntimeBlockMapping instance;

    static {
        instance = new RuntimeBlockMapping();
    }

    private final Map<Integer, Map<Integer, NbtMap>> bedrockKnownStates = new LinkedHashMap<>(32);
    private final Map<Integer, Map<Integer, Integer>> hashToRuntimeId = new HashMap<>(32);
    private final Map<Integer, Map<Integer, Integer>> runtimeIdToHash = new HashMap<>(32);

    public RuntimeBlockMapping() {
        load("canonical_block_states.nbt", (protocolId, rawData) -> {
            val map = new HashMap<Integer, NbtMap>(20000);
            val states = new LinkedList<NbtMap>();
            try {
                val reader = new BinaryStream(rawData.readAllBytes());
                val nbtReader = NbtUtils.createNetworkReader(reader);
                while (!reader.feof()) {
                    NbtMap blockState = (NbtMap) nbtReader.readTag();
                    var nbt = BlockStateUpdaters.updateBlockState(blockState, BlockStateUpdaters.LATEST_VERSION);
                    nbt = NbtMap.builder()
                            .putString("name", nbt.getString("name"))
                            .putCompound("states", nbt.getCompound("states"))
                            .build();
                    var hashCode = nbt.hashCode();
                    map.put(hashCode, nbt);
                    states.add(nbt);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.bedrockKnownStates.put(protocolId, map);
            this.hashToRuntimeId.put(protocolId, new HashMap<>(20000));
            this.runtimeIdToHash.put(protocolId, new HashMap<>(20000));

            for (int i = 0; i < states.size(); i++) {
                val state = states.get(i);
                this.hashToRuntimeId.get(protocolId).put(state.hashCode(), i);
                this.runtimeIdToHash.get(protocolId).put(i, state.hashCode());
            }
        });
        log.debug("Loaded runtime block mappings");
    }

    public Integer toRuntimeId(int protocolId, int hash) {
        return this.hashToRuntimeId.get(protocolId).get(hash);
    }

    public Integer fromRuntimeId(int protocolId, int runtimeId) {
        return this.runtimeIdToHash.get(protocolId).get(runtimeId);
    }

    public Integer fromNbt(String name, InputStream input) {
        try {
            var reader = NbtUtils.createReaderLE(input);
            var tg = (NbtMap) reader.readTag();
            return toInternalId(name, tg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer toInternalId(String name, NbtMap tg) {
        tg = NbtMap.builder()
                .putString("name", name)
                .putCompound("states", tg.getCompound("states"))
                .build();
        var nbt = BlockStateUpdaters.updateBlockState(tg, BlockStateUpdaters.LATEST_VERSION);
        var tag = NbtMap.builder()
                .putString("name", nbt.getString("name"))
                .putCompound("states", nbt.getCompound("states"))
                .build();
        return tag.hashCode();
    }

    public Map<Integer, NbtMap> getBedrockKnownStates(int protocolId) {
        return this.bedrockKnownStates.get(protocolId);
    }

    public int getFallback(int protocolId) {
        for (val v : this.bedrockKnownStates.get(protocolId).entrySet()) {
            if (v.getValue().get("name").equals("minecraft:info_update")) {
                return this.toRuntimeId(protocolId, v.getKey());
            }
        }
        throw new RuntimeException("no fallback minecraft:info_update found.");
    }
}