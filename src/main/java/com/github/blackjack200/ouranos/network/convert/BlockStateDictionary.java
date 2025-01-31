package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.utils.HashUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.InputStreamReader;
import java.util.*;

@Log4j2
public final class BlockStateDictionary extends AbstractMapping {
    public static final class Dictionary {
        private final Map<Integer, BlockEntry> stateHashToEntry = new HashMap<>();
        private final Map<String, Map<Integer, BlockEntry>> stateIdToMetaToEntry = new HashMap<>();
        private final Map<Integer, Integer> stateHashToRuntimeId = new HashMap<>();
        private final Map<Integer, Integer> runtimeToStateHash = new HashMap<>();
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
            val str = new String[]{
                    "minecraft:wither_skeleton_skull",
                    "minecraft:zombie_head",
                    "minecraft:player_head",
                    "minecraft:creeper_head",
                    "minecraft:piglin_head",
                    "minecraft:dragon_head",
            };
            for (val id : str) {
                if (!this.stateIdToMetaToEntry.containsKey(id)) {
                    this.stateIdToMetaToEntry.put(id, this.stateIdToMetaToEntry.get("minecraft:skeleton_skull"));
                }
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
            if (!stateIdToMetaToEntry.containsKey(id)) {
                return null;
            }
            if (!stateIdToMetaToEntry.get(id).containsKey(meta)) {
                return null;
            }
            return stateIdToMetaToEntry.get(id).get(meta).stateHash;
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
                var rawState = (NbtMap) reader.readTag();
                var state = BlockStateUpdaters.updateBlockState(rawState, BlockStateUpdaters.LATEST_VERSION);

                //TODO HACK! blame on BlockStateUpdaters
                if (state.getString("name").equals("minecraft:0tnt")) {
                    state = state.toBuilder().putString("name", "minecraft:tnt").build();
                }
                if (state.getString("name").equals("minecraft:1tnt")) {
                    state = state.toBuilder().putString("name", "minecraft:underwater_tnt").build();
                }

                var stateHash = HashUtils.computeBlockStateHash(state.getString("name"), state);
                var meta = meta_map.get(i);
                if (meta == null) {
                    throw new RuntimeException("Missing associated meta value for state " + i + " (" + state + ")");
                }
                list.add(new BlockEntry(state.getString("name"), meta, rawState, stateHash));
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