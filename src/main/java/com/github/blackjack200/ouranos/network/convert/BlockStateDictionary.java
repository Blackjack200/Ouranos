package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.utils.HashUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.InputStreamReader;
import java.util.*;

public final class BlockStateDictionary extends AbstractMapping {
    public static final class Dictionary {
        private final Map<Integer, BlockEntry> stateHashToEntry = new HashMap<>();
        private final Map<String, Map<Integer, BlockEntry>> stateIdToMetaToEntry = new HashMap<>();
        private final Map<Integer, Integer> stateHashToRuntimeId = new HashMap<>();
        private final Map<Integer, Integer> runtimeToStateHash = new HashMap<>();
        private Map<String, Map<Integer, Integer>> stringMapHashMap;
        @Getter
        private Integer fallback;

        public Dictionary(List<BlockEntry> states) {
            for (int runtimeId = 0, stateIdMax = states.size(); runtimeId < stateIdMax; runtimeId++) {
                var entry = states.get(runtimeId);
                this.stateHashToEntry.put(entry.stateHash, entry);

                this.stateIdToMetaToEntry.computeIfAbsent(entry.name, k -> new HashMap<>());
                this.stateIdToMetaToEntry.get(entry.name).put(entry.meta, entry);

                this.stateHashToRuntimeId.put(entry.stateHash, runtimeId);
                this.runtimeToStateHash.put(runtimeId, entry.stateHash);
            }
            for (val v : this.stateHashToEntry.entrySet()) {
                if (v.getValue().name.equals("minecraft:info_update")) {
                    this.fallback = Optional.of(this.toRuntimeId(v.getKey())).get();
                    break;
                }
            }
            if (this.fallback == null) {
                throw new RuntimeException("no fallback minecraft:info_update found.");
            }
        }

        public Integer toRuntimeId(int hash) {
            return this.stateHashToRuntimeId.get(hash);
        }

        public Integer toStateHash(int runtimeId) {
            return this.runtimeToStateHash.get(runtimeId);
        }

        /**
         * Returns the state ID associated with a given blockstate data.
         *
         * @param name  containing the name.
         * @param state containing the states.
         * @return the state ID or null if no match.
         */
        public Integer lookupStateIdFromData(String name, NbtMap state) {
            var stateHash = HashUtils.computeBlockStateHash(name, state);
            if (stateHashToEntry.containsKey(stateHash)) {
                return stateHash;
            }
            return null;
        }

        public BlockEntry lookupStateFromStateHash(int stateHash) {
            return this.stateHashToEntry.get(stateHash);
        }

        /**
         * Returns the blockstate meta value for the given state ID.
         *
         * @param networkRuntimeId the state ID.
         * @return the meta value or null.
         */
        public Integer getMetaFromStateId(int networkRuntimeId) {
            return this.stateHashToEntry.get(this.runtimeToStateHash.get(networkRuntimeId)).meta;
        }

        /**
         * Searches for the appropriate state ID which matches the given blockstate ID and meta value.
         *
         * @param id   the blockstate ID.
         * @param meta the blockstate meta value.
         * @return the state ID or null if no match.
         */
        public Integer lookupStateIdFromIdMeta(String id, int meta) {
            if (stringMapHashMap.isEmpty()) {
                stringMapHashMap = new HashMap<>();
                for (var metaToEntryEntry : this.stateIdToMetaToEntry.entrySet()) {
                    for (var entry : metaToEntryEntry.getValue().entrySet()) {
                        if (entry.getValue().name.equals(id)) {
                            stringMapHashMap.computeIfAbsent(id, k -> new HashMap<>());
                            stringMapHashMap.get(id).put(meta, entry.getValue().stateHash);
                        }
                    }
                }
            }
            return stringMapHashMap.get(id).get(meta);
        }

        public record BlockEntry(String name, int meta, NbtMap stateData, int stateHash) {
        }

        @SneakyThrows
        private static Dictionary load(int protocolId) {
            var block_state = open(lookupAvailableFile("canonical_block_states.nbt", protocolId));
            var meta_map = new Gson().fromJson(new InputStreamReader(open(lookupAvailableFile("block_state_meta_map.json", protocolId))), new TypeToken<List<Integer>>() {
            });
            var reader = NbtUtils.createNetworkReader(block_state);
            var list = new LinkedList<BlockEntry>();
            int i = 0;
            while (block_state.available() > 0) {
                NbtMap blockState = (NbtMap) reader.readTag();
                var state = BlockStateUpdaters.updateBlockState(blockState, BlockStateUpdaters.LATEST_VERSION);
                var stateHash = HashUtils.computeBlockStateHash(state.getString("name"), state);
                var meta = meta_map.get(i);
                if (meta == null) {
                    throw new RuntimeException("Missing associated meta value for state " + i + " (" + state + ")");
                }
                list.add(new BlockEntry(state.getString("name"), meta, blockState, stateHash));
                i++;
            }
            return new Dictionary(list);
        }
    }

    private static final Map<Integer, Dictionary> entries = new HashMap<>();

    public static Dictionary getInstance(int protocol) {
        if (!entries.containsKey(protocol)) {
            entries.put(protocol, Dictionary.load(protocol));
        }
        return entries.get(protocol);
    }
}