package com.blackjack200.ouranos.network.data.bedrock.block;

import org.cloudburstmc.nbt.NbtMap;

public final class BlockStateData {

    // Current version of block state data in a 32-bit format
    public static final int CURRENT_VERSION = (1 << 24) | // major
            (21 << 16) | // minor
            (40 << 8) | // patch
            1; // revision

    public static final String TAG_NAME = "name";
    public static final String TAG_STATES = "states";
    public static final String TAG_VERSION = "version";

    private final String name;
    private final NbtMap states;
    private final int version;

    // Constructor
    public BlockStateData(String name, NbtMap states, int version) {
        this.name = name;
        this.states = states;
        this.version = version;
    }

    // Static method to return the current block state data with the latest version
    public static BlockStateData current(String name, NbtMap states) {
        return new BlockStateData(name, states, CURRENT_VERSION);
    }

    // Getters
    public String getName() {
        return name;
    }

    public NbtMap getStates() {
        return states;
    }

    public Tag getState(String name) {
        return states.get(name);
    }

    public int getVersion() {
        return version;
    }

    // Return version as a string in format major.minor.patch.revision
    public String getVersionAsString() {
        int major = (version >> 24) & 0xff;
        int minor = (version >> 16) & 0xff;
        int patch = (version >> 8) & 0xff;
        int revision = version & 0xff;
        return major + "." + minor + "." + patch + "." + revision;
    }

    // Deserialize from NBT (assuming NBT structure has similar methods to the ones in the PHP version)
    public static BlockStateData fromNbt(NbtMap nbt) throws BlockStateDeserializeException {
        try {
            String name = nbt.getString(TAG_NAME);
            NbtMap statesTag = nbt.getCompound(TAG_STATES);
            if (statesTag == null) {
                throw new BlockStateDeserializeException("Missing tag \"" + TAG_STATES + "\"");
            }
            int version = nbt.getInt(TAG_VERSION, 0);

            // TODO: read version from VersionInfo::TAG_WORLD_DATA_VERSION for older blockstate versions

            // Deserialize additional keys and check for unexpected ones
            NbtMap allKeys = nbt.getValue();
            allKeys.remove(TAG_NAME);
            allKeys.remove(TAG_STATES);
            allKeys.remove(TAG_VERSION);
            allKeys.remove(VersionInfo.TAG_WORLD_DATA_VERSION);

            if (!allKeys.isEmpty()) {
                throw new BlockStateDeserializeException("Unexpected extra keys: " + String.join(", ", allKeys.keySet()));
            }

            return new BlockStateData(name, statesTag.getValue(), version);
        } catch (NbtException e) {
            throw new BlockStateDeserializeException(e.getMessage(), e);
        }
    }

    // Convert to vanilla NBT representation
    public NbtMap toVanillaNbt() {
        var statesTag = NbtMap.builder();
        statesTag.putAll(states);
        return NbtMap.builder()
                .putString(TAG_NAME, name)
                .putInt(TAG_VERSION, version)
                .putCompound(TAG_STATES, statesTag.build()).build();
    }

    // Convert to NBT representation with extra PM-specific metadata for bug fixes
    public NbtMap toNbt() {
        return toVanillaNbt().toBuilder()
                .putLong(TAG_WORLD_DATA_VERSION, WORLD_DATA_VERSION);
    }

    // Check equality with another BlockStateData instance
    public boolean equals(BlockStateData that) {
        if (!this.name.equals(that.name) || this.states.size() != that.states.size()) {
            return false;
        }
        for (var entry : this.states.entrySet()) {
            var thatTag = that.states.get(entry.getKey());
            if (thatTag == null || !thatTag.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
