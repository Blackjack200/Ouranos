package com.blackjack200.ouranos.network.data.bedrock;

public class VersionInfo {
    /**
     * PocketMine-MP-specific version ID for world data. Used to determine what fixes need to be applied to old world
     * data (e.g. stuff saved wrongly by past versions).
     * This version supplements the Minecraft vanilla world version.
     * <p>
     * This should be bumped if any **non-Mojang** BC-breaking change or bug fix is made to world save data of any kind
     * (entities, tiles, blocks, biomes etc.). For example, if PM accidentally saved a block with its facing value
     * swapped, we would bump this, but not if Mojang did the same change.
     */
    public static final int WORLD_DATA_VERSION = 1;
    /**
     * Name of the NBT tag used to store the world data version.
     */
    public static final String TAG_WORLD_DATA_VERSION = "PMMPDataVersion"; //TAG_Long

}
