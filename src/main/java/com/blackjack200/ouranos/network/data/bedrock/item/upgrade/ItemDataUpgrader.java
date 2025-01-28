package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import com.blackjack200.ouranos.network.data.bedrock.block.upgrade.BlockDataUpgrader;
import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import lombok.Getter;
import lombok.val;
import org.cloudburstmc.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

public class ItemDataUpgrader {
    private static final String TAG_LEGACY_ID = "id"; // TAG_Short (or TAG_String for Java item stacks)

    @Getter
    private final ItemIdMetaUpgrader idMetaUpgrader;
    private final LegacyItemIdToStringIdMap legacyIntToStringIdMap;
    private final R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap;
    private final BlockDataUpgrader blockDataUpgrader;
    private final BlockItemIdMap blockItemIdMap;

    public ItemDataUpgrader(ItemIdMetaUpgrader idMetaUpgrader,
                            LegacyItemIdToStringIdMap legacyIntToStringIdMap,
                            R12ItemIdToBlockIdMap r12ItemIdToBlockIdMap,
                            BlockDataUpgrader blockDataUpgrader,
                            BlockItemIdMap blockItemIdMap) {
        this.idMetaUpgrader = idMetaUpgrader;
        this.legacyIntToStringIdMap = legacyIntToStringIdMap;
        this.r12ItemIdToBlockIdMap = r12ItemIdToBlockIdMap;
        this.blockDataUpgrader = blockDataUpgrader;
        this.blockItemIdMap = blockItemIdMap;
    }

    /**
     * This function replaces the legacy ItemFactory::get().
     *
     * @throws RuntimeException if the legacy numeric ID doesn't map to a string ID
     */
    public SavedItemStackData upgradeItemTypeDataString(String rawNameId, int meta, int count, NbtMap nbt) throws RuntimeException {
        BlockStateData blockStateData = null;
        String r12BlockId = r12ItemIdToBlockIdMap.itemIdToBlockId(rawNameId);
        if (r12BlockId != null) {
            try {
                blockStateData = blockDataUpgrader.upgradeStringIdMeta(r12BlockId, meta);
            } catch (BlockStateDeserializeException e) {
                throw new RuntimeException("Failed to deserialize blockstate for legacy blockitem: " + e.getMessage(), e);
            }
        }

        String[] upgraded = idMetaUpgrader.upgrade(rawNameId, meta);
        String newNameId = upgraded[0];
        int newMeta = Integer.parseInt(upgraded[1]);

        return new SavedItemStackData(new SavedItemData(newNameId, newMeta, blockStateData, nbt), count, null, null, new String[]{}, new String[]{});
    }

    public SavedItemStackData upgradeItemTypeDataInt(int legacyNumericId, int meta, int count, NbtMap nbt) throws RuntimeException {
        String rawNameId = legacyIntToStringIdMap.legacyToString(legacyNumericId);
        if (rawNameId == null) {
            throw new RuntimeException("Unmapped legacy item ID " + legacyNumericId);
        }
        return upgradeItemTypeDataString(rawNameId, meta, count, nbt);
    }

    private SavedItemData upgradeItemTypeNbt(org.cloudburstmc.nbt.NbtMap tag) throws RuntimeException {
        String rawNameId;
        ShortTag nameIdTag = tag.getTag(SavedItemData.TAG_NAME);
        if (nameIdTag != null) {
            rawNameId = nameIdTag.getValue();
        } else {
            ShortTag idTag = tag.getTag(TAG_LEGACY_ID);
            if (idTag != null) {
                if (idTag.getValue() == 0) {
                    return null; // Air case
                }
                rawNameId = legacyIntToStringIdMap.legacyToString(idTag.getValue());
                if (rawNameId == null) {
                    throw new RuntimeException("Legacy item ID " + idTag.getValue() + " doesn't map to any modern string ID");
                }
            } else {
                throw new RuntimeException("Item stack data should have either a name ID or a legacy ID");
            }
        }

        int meta = tag.getShort(SavedItemData.TAG_DAMAGE, 0);
        NbtMap blockStateNbt = tag.getNbtMap(SavedItemData.TAG_BLOCK);
        BlockStateData blockStateData = null;
        if (blockStateNbt != null) {
            try {
                blockStateData = blockDataUpgrader.upgradeBlockStateNbt(blockStateNbt);
            } catch (BlockStateDeserializeException e) {
                throw new RuntimeException("Failed to deserialize blockstate for blockitem: " + e.getMessage(), e);
            }
        } else {
            String r12BlockId = r12ItemIdToBlockIdMap.itemIdToBlockId(rawNameId);
            if (r12BlockId != null) {
                try {
                    blockStateData = blockDataUpgrader.upgradeStringIdMeta(r12BlockId, meta);
                } catch (BlockStateDeserializeException e) {
                    throw new RuntimeException("Failed to deserialize blockstate for legacy blockitem: " + e.getMessage(), e);
                }
            }
        }

        String[] upgraded = idMetaUpgrader.upgrade(rawNameId, meta);
        String newNameId = upgraded[0];
        int newMeta = Integer.parseInt(upgraded[1]);

        // Handling block state and item data upgrades
        if (blockStateData == null) {
            String blockId = blockItemIdMap.lookupBlockId(newNameId);
            if (blockId != null) {
                BlockStateDictionary blockStateDictionary = TypeConverter.getInstance().getBlockTranslator().getBlockStateDictionary();
                NetworkRuntimeId networkRuntimeId = blockStateDictionary.lookupStateIdFromIdMeta(blockId, 0);
                if (networkRuntimeId == null) {
                    throw new RuntimeException("Failed to find blockstate for blockitem " + newNameId);
                }
                blockStateData = blockStateDictionary.generateDataFromStateId(networkRuntimeId);
            }
        }

        return new SavedItemData(newNameId, newMeta, blockStateData, tag.getNbtMap(SavedItemData.TAG_TAG));
    }

    public SavedItemStackData upgradeItemStackNbt(org.cloudburstmc.nbt.NbtMap tag) throws RuntimeException {
        SavedItemData savedItemData = upgradeItemTypeNbt(tag);
        if (savedItemData == null) {
            return null; // Air case
        }

        try {
            int count = Binary.unsignByte(tag.getByte(SavedItemStackData.TAG_COUNT));
            ByteTag slotTag = tag.getTag(SavedItemStackData.TAG_SLOT);
            Byte slot = (slotTag != null) ? Binary.unsignByte(slotTag.getValue()) : null;
            ByteTag wasPickedUpTag = tag.getTag(SavedItemStackData.TAG_WAS_PICKED_UP);
            Byte wasPickedUp = (wasPickedUpTag != null) ? wasPickedUpTag.getValue() : null;

            ListTag canPlaceOnList = tag.getListTag(SavedItemStackData.TAG_CAN_PLACE_ON);
            ListTag canDestroyList = tag.getListTag(SavedItemStackData.TAG_CAN_DESTROY);

            return new SavedItemStackData(
                    savedItemData,
                    count,
                    slot,
                    wasPickedUp != 0,
                    deserializeListOfStrings(canPlaceOnList, SavedItemStackData.TAG_CAN_PLACE_ON),
                    deserializeListOfStrings(canDestroyList, SavedItemStackData.TAG_CAN_DESTROY)
            );
        } catch (NBTException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static List<String> deserializeListOfStrings(NbtList<String> list, String tagName) throws RuntimeException {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (val item : list) {
                if (item != null) {
                    result.add(item);
                } else {
                    throw new RuntimeException("Unexpected type of list for tag '" + tagName + "', expected TAG_String");
                }
            }
        }
        return result;
    }
}
