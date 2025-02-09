package com.github.blackjack200.ouranos.data.bedrock.block;

import com.github.blackjack200.ouranos.data.bedrock.VersionInfo;
import org.cloudburstmc.nbt.NbtMap;

public final class BlockStateData {
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

    // Getters
    public String getName() {
        return name;
    }

    public NbtMap getStates() {
        return states;
    }

    public Object getState(String name) {
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
    public static BlockStateData fromNbt(NbtMap nbt) {
        try {
            String name = nbt.getString(TAG_NAME);
            NbtMap statesTag = nbt.getCompound(TAG_STATES);
            if (statesTag == null) {
                throw new RuntimeException("Missing tag \"" + TAG_STATES + "\"");
            }
            int version = nbt.getInt(TAG_VERSION, 0);

            // TODO: read version from VersionInfo::TAG_WORLD_DATA_VERSION for older blockstate versions

            // Deserialize additional keys and check for unexpected ones
            var allKeys = nbt.toBuilder();
            allKeys.remove(TAG_NAME);
            allKeys.remove(TAG_STATES);
            allKeys.remove(TAG_VERSION);
            allKeys.remove(VersionInfo.TAG_WORLD_DATA_VERSION);

            if (!allKeys.isEmpty()) {
                throw new RuntimeException("Unexpected extra keys: " + String.join(", ", allKeys.keySet()));
            }

            return new BlockStateData(name, statesTag, version);
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // Convert to vanilla NBT representation
    public NbtMap toVanillaNbt() {
        var statesTag = NbtMap.builder();
        statesTag.putAll(states);
        return NbtMap.builder()
                .putString(TAG_NAME, name)
                .putCompound(TAG_STATES, statesTag.build())
                .putInt(TAG_VERSION, version).build();
    }

    // Convert to NBT representation with extra PM-specific metadata for bug fixes
    public NbtMap toNbt() {
        return toVanillaNbt().toBuilder()
                .putLong(VersionInfo.TAG_WORLD_DATA_VERSION, VersionInfo.WORLD_DATA_VERSION).build();
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
