package com.blackjack200.ouranos.network.data.bedrock.item.upgrade;

import com.blackjack200.ouranos.network.data.bedrock.block.BlockStateData;
import com.blackjack200.ouranos.network.data.bedrock.block.upgrade.BlockDataUpgrader;
import com.blackjack200.ouranos.network.data.bedrock.item.BlockItemIdMap;
import com.blackjack200.ouranos.network.data.bedrock.item.SavedItemData;
import com.blackjack200.ouranos.network.data.bedrock.item.SavedItemStackData;
import lombok.Getter;
import lombok.val;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

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

        var upgraded = idMetaUpgrader.upgrade(rawNameId, meta);
        String newNameId = upgraded[0].toString();
        int newMeta = (Integer) upgraded[1];

        return new SavedItemStackData(new SavedItemData(newNameId, newMeta, blockStateData, nbt), count, null, null, new String[]{}, new String[]{});
    }

    public SavedItemStackData upgradeItemTypeDataInt(int legacyNumericId, int meta, int count, NbtMap nbt) throws RuntimeException {
        String rawNameId = legacyIntToStringIdMap.legacyToString(legacyNumericId);
        if (rawNameId == null) {
            throw new RuntimeException("Unmapped legacy item ID " + legacyNumericId);
        }
        return upgradeItemTypeDataString(rawNameId, meta, count, nbt);
    }

    private SavedItemData upgradeItemTypeNbt(NbtMap tag) throws RuntimeException {
        var rawNameId = "";

        var nameIdTag = tag.get(SavedItemData.TAG_NAME);
        if (nameIdTag instanceof String tg) {
            // Bedrock 1.6+
            rawNameId = tg;
        } else {
            var idTag = tag.get(TAG_LEGACY_ID);
            if (idTag instanceof Short tg) {
                // Bedrock <= 1.5, PM <= 1.12
                if (tg == 0) {
                    // 0 is a special case for air, which is not a valid item ID
                    // this isn't supposed to be saved, but this appears in some places due to bugs in older versions
                    return null;
                }
                rawNameId = legacyIntToStringIdMap.legacyToString(tg);
                if (rawNameId == null) {
                    throw new SavedDataLoadingException("Legacy item ID " + tg + " doesn't map to any modern string ID");
                }
            } else if (idTag instanceof String tg) {
                // PC item save format - best we can do here is hope the string IDs match
                rawNameId = tg;
            } else {
                throw new SavedDataLoadingException("Item stack data should have either a name ID or a legacy ID");
            }
        }

        var meta = tag.getShort(SavedItemData.TAG_DAMAGE, (short) 0);

        var blockStateNbt = tag.getCompound(SavedItemData.TAG_BLOCK);
        var blockStateData = (BlockStateData) null;
        if (blockStateNbt != null) {
            try {
                blockStateData = blockDataUpgrader.upgradeBlockStateNbt(blockStateNbt);
            } catch (BlockStateDeserializeException e) {
                throw new SavedDataLoadingException("Failed to deserialize blockstate for blockitem: " + e.getMessage(), 0, e);
            }
        } else {
            var r12BlockId = r12ItemIdToBlockIdMap.itemIdToBlockId(rawNameId);
            if (r12BlockId != null) {
                // this is a legacy blockitem represented by ID + meta
                try {
                    blockStateData = blockDataUpgrader.upgradeStringIdMeta(r12BlockId, meta);
                } catch (BlockStateDeserializeException e) {
                    throw new SavedDataLoadingException("Failed to deserialize blockstate for legacy blockitem: " + e.getMessage(), 0, e);
                }
            }
        }

        // probably a standard item
        if (blockStateData == null) {
            blockStateData = null;
        }

        // Upgrade the ID and meta data
        var newNameId = idMetaUpgrader.upgrade(rawNameId, meta)[0];
        var newMeta = idMetaUpgrader.upgrade(rawNameId, meta)[1];

        // Dirty hack to load old skulls from disk (before Mojang makes something with a non-0 default state)
        if (blockStateData == null) {
            var blockId = blockItemIdMap.lookupBlockId(newNameId);
            if (blockId != null) {
                var blockStateDictionary = TypeConverter.getInstance().getBlockTranslator().getBlockStateDictionary();
                var networkRuntimeId = blockStateDictionary.lookupStateIdFromIdMeta(blockId, 0);

                if (networkRuntimeId == null) {
                    throw new SavedDataLoadingException("Failed to find blockstate for blockitem " + newNameId);
                }

                blockStateData = blockStateDictionary.generateDataFromStateId(networkRuntimeId);
            }
        }

        // TODO: this won't account for spawn eggs from before 1.16.100 - perhaps we're lucky and they just left the meta in there anyway?
        // TODO: read version from VersionInfo.TAG_WORLD_DATA_VERSION - we may need it to fix up old items

        return new SavedItemData(newNameId, newMeta, blockStateData, tag.getCompoundTag(SavedItemData.TAG_TAG));
    }

    public SavedItemStackData upgradeItemStackNbt(NbtMap tag) throws RuntimeException {
        SavedItemData savedItemData = upgradeItemTypeNbt(tag);
        if (savedItemData == null) {
            return null; // Air case
        }

        try {
            int count = Binary.unsignByte(tag.getByte(SavedItemStackData.TAG_COUNT));
            byte slotTag = tag.getTag(SavedItemStackData.TAG_SLOT);
            Byte slot = (slotTag != null) ? Binary.unsignByte(slotTag.getValue()) : null;
            byte wasPickedUpTag = tag.getTag(SavedItemStackData.TAG_WAS_PICKED_UP);
            Byte wasPickedUp = (wasPickedUpTag != null) ? wasPickedUpTag.getValue() : null;

            List<String> canPlaceOnList = tag.getList(SavedItemStackData.TAG_CAN_PLACE_ON, NbtType.STRING);
            List<String> canDestroyList = tag.getList(SavedItemStackData.TAG_CAN_DESTROY, NbtType.STRING);

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
