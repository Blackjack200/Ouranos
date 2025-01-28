package com.blackjack200.ouranos.network.data.bedrock.item;

import lombok.Getter;

import java.util.List;

@Getter
public final class SavedItemStackData {

    public static final String TAG_COUNT = "Count";
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_WAS_PICKED_UP = "WasPickedUp";
    public static final String TAG_CAN_PLACE_ON = "CanPlaceOn";
    public static final String TAG_CAN_DESTROY = "CanDestroy";

    private final SavedItemData typeData;
    private final int count;
    private final Integer slot;
    private final Boolean wasPickedUp;
    private final List<String> canPlaceOn;
    private final List<String> canDestroy;

    /**
     * @param canPlaceOn A list of strings representing places where the item can be placed.
     * @param canDestroy A list of strings representing blocks the item can destroy.
     */
    public SavedItemStackData(SavedItemData typeData, int count, Integer slot, Boolean wasPickedUp, List<String> canPlaceOn, List<String> canDestroy) {
        this.typeData = typeData;
        this.count = count;
        this.slot = slot;
        this.wasPickedUp = wasPickedUp;
        this.canPlaceOn = canPlaceOn;
        this.canDestroy = canDestroy;
    }

    public CompoundTag toNbt() {
        CompoundTag result = new CompoundTag();

        result.setByte(TAG_COUNT, (byte) count);

        if (slot != null) {
            result.setByte(TAG_SLOT, (byte) slot.intValue());
        }

        if (wasPickedUp != null) {
            result.setByte(TAG_WAS_PICKED_UP, (byte) (wasPickedUp ? 1 : 0));
        }

        if (!canPlaceOn.isEmpty()) {
            ListTag canPlaceOnTag = new ListTag();
            for (String s : canPlaceOn) {
                canPlaceOnTag.add(new StringTag(s));
            }
            result.setTag(TAG_CAN_PLACE_ON, canPlaceOnTag);
        }

        if (!canDestroy.isEmpty()) {
            ListTag canDestroyTag = new ListTag();
            for (String s : canDestroy) {
                canDestroyTag.add(new StringTag(s));
            }
            result.setTag(TAG_CAN_DESTROY, canDestroyTag);
        }

        result.merge(typeData.toNbt());

        return result;
    }
}
