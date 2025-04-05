package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.utils.HashUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class BlockStateDictionary extends AbstractMapping {
    public static final class Dictionary {
        private final Map<Integer, BlockEntry> latestStateHashToEntry;
        private final Map<Integer, Integer> latestStateHashToCurrent;
        private final Map<String, Map<Integer, BlockEntry>> latestStateHashToMetaToEntry;
        private final Map<Integer, Integer> latestStateHashToRuntimeId;
        private final Map<Integer, Integer> runtimeToLatestStateHash;
        @Getter
        private Integer fallbackRuntimeId;
        @Getter
        private Integer fallbackCurrentStateHash;

        public Dictionary(Int2ObjectRBTreeMap<BlockEntry> states) {
            this.latestStateHashToEntry = new Int2ObjectRBTreeMap<>();
            this.latestStateHashToCurrent = new Int2ObjectRBTreeMap<>();
            this.latestStateHashToMetaToEntry = new HashMap<>();
            this.latestStateHashToRuntimeId = new Int2ObjectRBTreeMap<>();
            this.runtimeToLatestStateHash = new Int2ObjectRBTreeMap<>();

            for (int runtimeId = 0, stateIdMax = states.size(); runtimeId < stateIdMax; runtimeId++) {
                var entry = states.get(runtimeId);
                this.latestStateHashToEntry.put(entry.latestStateHash, entry);
                this.latestStateHashToCurrent.put(entry.latestStateHash, entry.currentStateHash);

                this.latestStateHashToMetaToEntry.computeIfAbsent(entry.name, k -> new Int2ObjectRBTreeMap<>());
                this.latestStateHashToMetaToEntry.get(entry.name).put(entry.meta, entry);

                this.latestStateHashToRuntimeId.put(entry.latestStateHash, runtimeId);
                this.runtimeToLatestStateHash.put(runtimeId, entry.latestStateHash);
            }

            for (val v : this.latestStateHashToEntry.entrySet()) {
                if (v.getValue().name.equals("minecraft:info_update")) {
                    this.fallbackRuntimeId = this.toRuntimeId(v.getKey());
                    this.fallbackCurrentStateHash = v.getValue().currentStateHash;
                    break;
                }
            }
            if (this.fallbackRuntimeId == null) {
                throw new RuntimeException("no fallback minecraft:info_update found.");
            }
        }

        public Integer toRuntimeId(int latestStateHash) {
            return this.latestStateHashToRuntimeId.get(latestStateHash);
        }

        public Integer toLatestStateHash(int runtimeId) {
            return this.runtimeToLatestStateHash.get(runtimeId);
        }

        public Integer toCurrentStateHash(int latestStateHash) {
            return this.latestStateHashToCurrent.get(latestStateHash);
        }

        public BlockEntry toBlockState(int runtimeId) {
            return this.latestStateHashToEntry.get(this.runtimeToLatestStateHash.get(runtimeId));
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
            if (latestStateHashToEntry.containsKey(stateHash)) {
                return stateHash;
            }
            return null;
        }

        public BlockEntry lookupStateFromStateHash(int stateHash) {
            return this.latestStateHashToEntry.get(stateHash);
        }

        /**
         * Searches for the appropriate state which matches the given blockstate ID and meta value.
         *
         * @param id   the blockstate ID.
         * @param meta the blockstate meta value.
         * @return the state ID or null if no match.
         */
        public BlockEntry lookupStateIdFromIdMeta(String id, int meta) {
            if (!latestStateHashToMetaToEntry.containsKey(id)) {
                return null;
            }
            if (!latestStateHashToMetaToEntry.get(id).containsKey(meta)) {
                return null;
            }
            return latestStateHashToMetaToEntry.get(id).get(meta);
        }

        public record BlockEntry(String name, String newName, int meta, NbtMap rawState, int latestStateHash,
                                 int currentStateHash) {
        }

        @SneakyThrows
        private static Dictionary load(int protocolId) {
            var block_state = open(lookupAvailableFile("canonical_block_states.nbt", protocolId));
            var meta_map = new Gson().fromJson(new InputStreamReader(open(lookupAvailableFile("block_state_meta_map.json", protocolId))), new TypeToken<List<Integer>>() {
            });
            var reader = NbtUtils.createNetworkReader(block_state);
            var list = new Int2ObjectRBTreeMap<BlockEntry>();
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

                var latestStateHash = HashUtils.computeBlockStateHash(state.getString("name"), state);
                var meta = meta_map.get(i);
                if (meta == null) {
                    throw new RuntimeException("Missing associated meta value for state " + i + " (" + state + ")");
                }
                list.put(list.size(), new BlockEntry(rawState.getString("name"), state.getString("name"), meta, rawState, latestStateHash, HashUtils.computeBlockStateHash(rawState)));
                i++;
            }
            return new Dictionary(list);
        }
    }

    private static final Map<Integer, Dictionary> entries = new ConcurrentHashMap<>();


    public static Dictionary getInstance(int protocol) {
        return entries.computeIfAbsent(protocol, Dictionary::load);
    }
}