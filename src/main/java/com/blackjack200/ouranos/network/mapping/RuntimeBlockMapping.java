package com.blackjack200.ouranos.network.mapping;

import com.blackjack200.ouranos.network.mapping.types.R12ToCurrentBlockMapEntry;
import com.blackjack200.ouranos.utils.BinaryStream;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtUtils;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class RuntimeBlockMapping extends AbstractMapping {
    private static RuntimeBlockMapping instance;

    static {
        instance = new RuntimeBlockMapping();
    }

    public static RuntimeBlockMapping getInstance() {
        return instance;
    }

    private Map<Integer, Map<Integer, Integer>> legacyToRuntime = new LinkedHashMap<>();
    private Map<Integer, Map<Integer, Integer>> runtimeToLegacy = new LinkedHashMap<>();
    private Map<Integer, List<NbtMap>> bedrockKnownStates = new LinkedHashMap<>();


    public RuntimeBlockMapping() {
        load("canonical_block_states.nbt", (protocolId, rawData) -> {
            val list = new ArrayList<NbtMap>();
            val reader = new BinaryStream(rawData.getBytes(StandardCharsets.UTF_8));
            val nbtReader = NbtUtils.createNetworkReader(reader);
            while (!reader.feof()) {
                try {
                    list.add((NbtMap) nbtReader.readTag());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.bedrockKnownStates.put(protocolId, list);
        });
        load("r12_to_current_block_map.bin", (protocolId, rawData) -> {
            this.runtimeToLegacy.put(protocolId, new LinkedHashMap<>());
            this.legacyToRuntime.put(protocolId, new LinkedHashMap<>());
            val legacyStateMap = new ArrayList<R12ToCurrentBlockMapEntry>();
            val reader = new BinaryStream(rawData.getBytes(StandardCharsets.UTF_8));
            while (!reader.feof()) {
                try {
                    val id = reader.getString();
                    val meta = reader.getLShort();

                    val offset = reader.getOffset();

                    val nbtReader = NbtUtils.createNetworkReader(reader);

                    val state = (NbtMap) nbtReader.readTag();
                    val d = new R12ToCurrentBlockMapEntry();
                    d.id = id;
                    d.meta = meta;
                    d.state = state;
                    legacyStateMap.add(d);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            val idToStateMap = new LinkedHashMap<String, ArrayList<Integer>>();
            val states = this.bedrockKnownStates.get(protocolId);

            for (int i = 0; i < states.size(); i++) {
                val state = states.get(i);
                val name = state.getString("name");
                idToStateMap.putIfAbsent(name, new ArrayList<>());
                idToStateMap.get(name).add(i);
            }

            legacyStateMap.forEach((pair) -> {
                val id = LegacyBlockIdToStringIdMap.getInstance().fromString(protocolId, pair.id);
                val data = pair.meta;
                if (data > 15) {
                    //we can't handle metadata with more than 4 bits;
                    return;
                }
                val mappedState = pair.state;
                val mappedName = mappedState.getString("name");
                if (!idToStateMap.containsKey(mappedName)) {
                    throw new RuntimeException("Mapped new state does not appear in network table");
                }
                idToStateMap.get(mappedName).forEach((k) -> {

                    val networkState = states.get(k);
                       registerMapping(protocolId, k, id, data);

                });
            });

            log.info(legacyStateMap.size());
        });
    }

    private void registerMapping(int protocolId, int staticRuntimeId, int legacyId, int legacyMeta) {
        int internalStateId = (legacyId << 4) | (legacyMeta & 0b1111);
        this.legacyToRuntime.get(protocolId).put(internalStateId, staticRuntimeId);
        this.runtimeToLegacy.get(protocolId).put(staticRuntimeId, internalStateId);
    }

    public int toRuntimeId(int protocolId, int internalStateId) {
        val v = this.legacyToRuntime.get(protocolId);
        return v.containsKey(internalStateId) ? v.get(internalStateId) : v.get(248 << 4);
    }

    public int fromRuntimeId(int protocolId, int runtimeId) {
        return this.runtimeToLegacy.get(protocolId).get(runtimeId);
    }
}
