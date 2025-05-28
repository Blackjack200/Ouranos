package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.AbstractMapping;
import com.github.blackjack200.ouranos.utils.HashUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class BlockStateDictionary extends AbstractMapping {
    public static final class Dictionary {
        private final Map<Integer, BlockEntry> latestStateHashToEntry;
        private final Map<Integer, Integer> latestStateHashToCurrent;
        private final Map<Integer, Integer> latestStateHashToRuntimeId;
        private final Map<Integer, Integer> runtimeToLatestStateHash;
        @Getter
        private Integer fallbackRuntimeId;
        @Getter
        private Integer airRuntimeId;
        @Getter
        private Integer fallbackCurrentStateHash;
        @Getter
        private final List<BlockEntry> knownStates;

        public Dictionary(Int2ObjectRBTreeMap<BlockEntry> states) {
            this.knownStates = states.values().stream().toList();
            this.latestStateHashToEntry = new Int2ObjectRBTreeMap<>();
            this.latestStateHashToCurrent = new Int2ObjectRBTreeMap<>();
            this.latestStateHashToRuntimeId = new Int2ObjectRBTreeMap<>();
            this.runtimeToLatestStateHash = new Int2ObjectRBTreeMap<>();

            for (int runtimeId = 0, stateIdMax = states.size(); runtimeId < stateIdMax; runtimeId++) {
                var entry = states.get(runtimeId);
                register(runtimeId, entry);
            }

            for (val v : this.latestStateHashToEntry.entrySet()) {
                if (v.getValue().name.equals("minecraft:info_update")) {
                    this.fallbackRuntimeId = this.toRuntimeId(v.getKey());
                    this.fallbackCurrentStateHash = v.getValue().currentStateHash;
                }
                if (v.getValue().name.equals("minecraft:air")) {
                    this.airRuntimeId = this.toRuntimeId(v.getKey());
                }
            }
            if (this.fallbackRuntimeId == null) {
                throw new RuntimeException("no fallback minecraft:info_update found.");
            }
            if (this.airRuntimeId == null) {
                throw new RuntimeException("no fallback minecraft:air found.");
            }
        }

        private void register(int runtimeId, BlockEntry entry) {
            this.latestStateHashToEntry.put(entry.latestStateHash, entry);
            this.latestStateHashToCurrent.put(entry.latestStateHash, entry.currentStateHash);

            this.latestStateHashToRuntimeId.put(entry.latestStateHash, runtimeId);
            this.runtimeToLatestStateHash.put(runtimeId, entry.latestStateHash);
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

        public record BlockEntry(String name, String newName, NbtMap rawState, int latestStateHash,
                                 int currentStateHash) {
        }

        @SneakyThrows
        private static Dictionary load(int protocolId) {
            var block_state = open(lookupAvailableFile("canonical_block_states.nbt", protocolId));
            var reader = NbtUtils.createNetworkReader(block_state);
            var list = new Int2ObjectRBTreeMap<BlockEntry>();
            while (block_state.available() > 0) {
                var rawTag = reader.readTag();
                if (rawTag instanceof NbtList<?> rawList) {
                    for (var rawEntry : rawList) {
                        var entry = (NbtMap) rawEntry;
                        var rawState = (NbtMap) entry.get("block");
                        var state = hackedUpgradeBlockState(rawState, BlockStateUpdaters.LATEST_VERSION);
                        var latestStateHash = HashUtils.computeBlockStateHash(state);
                        list.put(list.size(), new BlockEntry(rawState.getString("name"), state.getString("name"), rawState, latestStateHash, HashUtils.computeBlockStateHash(rawState)));
                    }
                } else if (rawTag instanceof NbtMap rawState) {
                    //TODO HACK! blame on BlockStateUpdaters
                    var state = hackedUpgradeBlockState(rawState, BlockStateUpdaters.LATEST_VERSION);
                    var latestStateHash = HashUtils.computeBlockStateHash(state.getString("name"), state);
                    list.put(list.size(), new BlockEntry(rawState.getString("name"), state.getString("name"), rawState, latestStateHash, HashUtils.computeBlockStateHash(rawState)));
                }
            }
            return new Dictionary(list);
        }
    }

    public static NbtMap hackedUpgradeBlockState(NbtMap tag, int version) {
        return fixBlockStateUpdaterIssue(BlockStateUpdaters.updateBlockState(tag, version));
    }

    private static NbtMap fixBlockStateUpdaterIssue(NbtMap state) {
        return state;
    }

    private static final Map<Integer, Dictionary> entries = new ConcurrentHashMap<>();


    public static Dictionary getInstance(int protocol) {
        return entries.computeIfAbsent(protocol, Dictionary::load);
    }
}