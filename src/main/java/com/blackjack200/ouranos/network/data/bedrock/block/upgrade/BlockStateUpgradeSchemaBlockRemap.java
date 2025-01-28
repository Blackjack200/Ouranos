package com.blackjack200.ouranos.network.data.bedrock.block.upgrade;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockStateUpgradeSchemaBlockRemap {

    private final Map<String, Tag> oldState;
    private final Object newName; // Can be either String or BlockStateUpgradeSchemaFlattenInfo
    private final Map<String, NbtM> newState;
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
            Map<String, Tag> oldState,
            Object newName,  // Can either be String or BlockStateUpgradeSchemaFlattenInfo
            Map<String, Tag> newState,
            List<String> copiedState
    ) {
        this.oldState = oldState;
        this.newName = newName;
        this.newState = newState;
        this.copiedState = copiedState;
    }

    public Map<String, Tag> getOldState() {
        return oldState;
    }

    public Object getNewName() {
        return newName;
    }

    public Map<String, Tag> getNewState() {
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
        for (Map.Entry<String, Tag> entry : this.oldState.entrySet()) {
            String propertyName = entry.getKey();
            Tag propertyValue = entry.getValue();
            Tag thatPropertyValue = that.oldState.get(propertyName);
            if (thatPropertyValue == null || !thatPropertyValue.equals(propertyValue)) {
                return false;
            }
        }

        // Check newState equality
        for (Map.Entry<String, Tag> entry : this.newState.entrySet()) {
            String propertyName = entry.getKey();
            Tag propertyValue = entry.getValue();
            Tag thatPropertyValue = that.newState.get(propertyName);
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

