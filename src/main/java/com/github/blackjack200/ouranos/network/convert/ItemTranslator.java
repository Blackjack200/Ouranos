package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

public class ItemTranslator {
    public static final String POLYFILL_ITEM_TAG = "____Ouranos____";

    public static ItemData recoverPolyfillItem(ItemData itemData) {
        var polyfillData = itemData.getTag().getCompound(POLYFILL_ITEM_TAG);
        if (polyfillData != NbtMap.EMPTY) {
            var itemDict = ItemTypeDictionary.getInstance(polyfillData.getInt("Source"));
            var builder = ItemData.builder()
                    .definition(itemDict.getEntries().get(itemDict.fromIntId(polyfillData.getInt("ItemId"))).toDefinition(polyfillData.getString("StringId")))
                    .tag(polyfillData.getCompound("Nbt"))
                    .count(itemData.getCount())
                    .damage(polyfillData.getInt("Meta"))
                    .netId(itemData.getNetId())
                    .canBreak(itemData.getCanBreak())
                    .canPlace(itemData.getCanPlace())
                    .blockingTicks(itemData.getBlockingTicks())
                    .usingNetId(polyfillData.getBoolean("UsingNetId"))
                    .netId(polyfillData.getInt("NetId"));
            if (polyfillData.containsKey("BlockId")) {
                builder.blockDefinition(new SimpleBlockDefinition(polyfillData.getInt("BlockId")));
            }
            return builder.build();
        }
        return null;
    }

    public static ItemData makePolyfillItem(int input, int output, ItemData itemData) {
        var def = itemData.getDefinition();
        var polyfillItem = ItemData.builder().usingNetId(itemData.isUsingNetId()).netId(itemData.getNetId()).count(itemData.getCount()).damage(0).definition(ItemTypeDictionary.getInstance(output).getEntries().get("minecraft:barrier").toDefinition("minecraft:barrier"));
        var polyfillData = NbtMap.builder()
                .putInt("Source", input)
                .putInt("Meta", itemData.getDamage())
                .putString("StringId", def.getIdentifier())
                .putBoolean("UsingNetId", itemData.isUsingNetId())
                .putInt("NetId", itemData.getNetId())
                .putString("StringId", def.getIdentifier())
                .putInt("ItemId", def.getRuntimeId());
        if (itemData.getBlockDefinition() != null) {
            polyfillData.putInt("BlockId", itemData.getBlockDefinition().getRuntimeId());
        }
        if (itemData.getTag() != null) {
            polyfillData.putCompound("Nbt", itemData.getTag());
        }
        polyfillItem.tag(NbtMap.builder()
                .putCompound("display", NbtMap.builder().putString("Name", def.getIdentifier()).build())
                .putCompound(POLYFILL_ITEM_TAG, polyfillData.build())
                .build()
        );
        return polyfillItem.build();
    }

}
