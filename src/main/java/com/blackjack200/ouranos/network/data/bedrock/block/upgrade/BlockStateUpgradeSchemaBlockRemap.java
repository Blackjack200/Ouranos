package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import org.cloudburstmc.nbt.NbtMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockStateUpgradeSchemaBlockRemap {

    private final NbtMap oldState;
    private final NbtMap newState;
    private final Object newName; // Can be either String or BlockStateUpgradeSchemaFlattenInfo
    private final List<String> copiedState;

    /**
     * Constructor.
     *
     * @param oldState    A map where key is a string and value is a Tag.
     * @param newName     Either a String or a BlockStateUpgradeSchemaFlattenInfo object.
     * @param newState    A map where key is a string and value is a Tag.
     * @param copiedState A list of strings.
     */
    public BlockStateUpgradeSchemaBlockRemap(
            NbtMap oldState,
            Object newName,  // Can either be String or BlockStateUpgradeSchemaFlattenInfo
            NbtMap newState,
            List<String> copiedState
    ) {
        this.oldState = oldState;
        this.newName = newName;
        this.newState = newState;
        this.copiedState = copiedState;
    }

    public NbtMap getOldState() {
        return oldState;
    }

    public Object getNewName() {
        return newName;
    }

    public NbtMap getNewState() {
        return newState;
    }

    public List<String> getCopiedState() {
        return copiedState;
    }

    /**
     * Compares two BlockStateUpgradeSchemaBlockRemap objects for equality.
     *
     * @param that Another BlockStateUpgradeSchemaBlockRemap to compare with.
     * @return true if both objects are equal, false otherwise.
     */
    public boolean equals(BlockStateUpgradeSchemaBlockRemap that) {
        if (that == null) {
            return false;
        }

        // Check if the newName is equal (either both String or both BlockStateUpgradeSchemaFlattenInfo)
        boolean sameName = this.newName.equals(that.newName) ||
                (this.newName instanceof BlockStateUpgradeSchemaFlattenInfo &&
                        that.newName instanceof BlockStateUpgradeSchemaFlattenInfo &&
                        ((BlockStateUpgradeSchemaFlattenInfo) this.newName).equals(that.newName));

        if (!sameName) {
            return false;
        }

        // Check if the sizes and copied state are the same
        if (this.oldState.size() != that.oldState.size() ||
                this.newState.size() != that.newState.size() ||
                this.copiedState.size() != that.copiedState.size() ||
                !this.copiedState.containsAll(that.copiedState)) {
            return false;
        }

        // Check oldState equality
        for (var entry : this.oldState.entrySet()) {
            String propertyName = entry.getKey();
            var propertyValue = entry.getValue();
            var thatPropertyValue = that.oldState.get(propertyName);
            if (thatPropertyValue == null || !thatPropertyValue.equals(propertyValue)) {
                return false;
            }
        }

        // Check newState equality
        for (var entry : this.newState.entrySet()) {
            String propertyName = entry.getKey();
            var propertyValue = entry.getValue();
            var thatPropertyValue = that.newState.get(propertyName);
            if (thatPropertyValue == null || !thatPropertyValue.equals(propertyValue)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockStateUpgradeSchemaBlockRemap that = (BlockStateUpgradeSchemaBlockRemap) obj;
        return equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldState, newName, newState, copiedState);
    }

    @Override
    public String toString() {
        return "BlockStateUpgradeSchemaBlockRemap{" +
                "oldState=" + oldState +
                ", newName=" + newName +
                ", newState=" + newState +
                ", copiedState=" + copiedState +
                '}';
    }
}

