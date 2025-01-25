package com.blackjack200.ouranos.network.convert;


import com.blackjack200.ouranos.utils.BinaryStream;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.util.*;

@Log4j2
public class RuntimeBlockMapping extends AbstractMapping {
    private static RuntimeBlockMapping instance;

    static {
        instance = new RuntimeBlockMapping();
    }

    public static RuntimeBlockMapping getInstance() {
        return instance;
    }

    private Map<Integer, Map<Integer, NbtMap>> bedrockKnownStates = new LinkedHashMap<>();
    private Map<Integer, List<NbtMap>> bedrockKnownStatesList = new LinkedHashMap<>();

    private Map<Integer, Map<Integer, Integer>> hashToRuntimeId = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> runtimeIdToHash = new HashMap<>();

    public RuntimeBlockMapping() {
        load("canonical_block_states.nbt", (protocolId, rawData) -> {
            val map = new HashMap<Integer, NbtMap>();
            val states = new LinkedList<NbtMap>();
            val reader = new BinaryStream(rawData);
            val nbtReader = NbtUtils.createNetworkReader(reader);
            while (!reader.feof()) {
                try {
                    var nbt = BlockStateUpdaters.updateBlockState((NbtMap) nbtReader.readTag(), BlockStateUpdaters.LATEST_VERSION);
                    nbt = NbtMap.builder()
                            .putString("name", nbt.getString("name"))
                            .putCompound("states", nbt.getCompound("states"))
                            .build();
                    int hashCo = nbt.hashCode();
                    map.put(hashCo, nbt);
                    states.add(nbt);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.bedrockKnownStates.put(protocolId, map);
            this.bedrockKnownStatesList.put(protocolId, states);
            this.hashToRuntimeId.put(protocolId, new HashMap<>());
            this.runtimeIdToHash.put(protocolId, new HashMap<>());

            for (int i = 0; i < states.size(); i++) {
                val state = states.get(i);
                val name = state.getString("name");
                this.hashToRuntimeId.get(protocolId).put(state.hashCode(), i);
                this.runtimeIdToHash.get(protocolId).put(i, state.hashCode());
            }
        });
    }

    public Integer translateBlock(int source, int target, int runtimeId) {
        val hash = this.runtimeIdToHash.get(source).get(runtimeId);
        if (hash == null) {
            return null;
        }
        return this.hashToRuntimeId.get(target).get(hash);
    }

    public Integer toRuntimeId(int protocolId, int hash) {
        return this.hashToRuntimeId.get(protocolId).get(hash);
    }

    public Integer fromRuntimeId(int protocolId, int runtimeId) {
        return this.runtimeIdToHash.get(protocolId).get(runtimeId);
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