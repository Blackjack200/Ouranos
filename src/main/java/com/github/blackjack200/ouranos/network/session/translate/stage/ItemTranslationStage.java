package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.packet.*;

public final class ItemTranslationStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }
        var input = context.inputProtocol();
        var output = context.outputProtocol();

        if (packet instanceof InventoryContentPacket pk) {
            val contents = new ArrayList<>(pk.getContents());
            contents.replaceAll(itemData ->
                TypeConverter.translateItemData(input, output, itemData)
            );
            pk.setContents(contents);
        } else if (packet instanceof CraftingDataPacket pk) {
            pk.getPotionMixData().clear();
            pk.getMaterialReducers().clear();
            pk.getCraftingData().clear();
            pk.getContainerMixData().clear();
            pk.setCleanRecipes(true);
        } else if (packet instanceof CreativeContentPacket pk) {
            val contents = new ArrayList<CreativeItemData>();
            for (var old : pk.getContents()) {
                contents.add(
                    TypeConverter.translateCreativeItemData(input, output, old)
                );
            }
            pk.getContents().clear();
            if (
                input < output &&
                output < Bedrock_v776.CODEC.getProtocolVersion()
            ) {
                pk.getContents().addAll(contents);
            }
            if (input >= output) {
                pk.getContents().addAll(contents);
            }
            val groups = new ArrayList<CreativeItemGroup>();
            for (var group : pk.getGroups()) {
                groups.add(
                    group
                        .toBuilder()
                        .icon(
                            TypeConverter.translateItemData(
                                input,
                                output,
                                group.getIcon()
                            )
                        )
                        .build()
                );
            }
            pk.getGroups().clear();
            pk.getGroups().addAll(groups);
        } else if (packet instanceof AddItemEntityPacket pk) {
            pk.setItemInHand(
                TypeConverter.translateItemData(
                    input,
                    output,
                    pk.getItemInHand()
                )
            );
        } else if (packet instanceof InventorySlotPacket pk) {
            pk.setItem(
                TypeConverter.translateItemData(input, output, pk.getItem())
            );
            pk.setStorageItem(
                TypeConverter.translateItemData(
                    input,
                    output,
                    pk.getStorageItem()
                )
            );
        } else if (packet instanceof InventoryTransactionPacket pk) {
            var newActions = new ArrayList<InventoryActionData>(
                pk.getActions().size()
            );
            for (var action : pk.getActions()) {
                newActions.add(
                    new InventoryActionData(
                        action.getSource(),
                        action.getSlot(),
                        TypeConverter.translateItemData(
                            input,
                            output,
                            action.getFromItem()
                        ),
                        TypeConverter.translateItemData(
                            input,
                            output,
                            action.getToItem()
                        ),
                        action.getStackNetworkId()
                    )
                );
            }
            pk.getActions().clear();
            pk.getActions().addAll(newActions);

            if (pk.getBlockDefinition() != null) {
                pk.setBlockDefinition(
                    TypeConverter.translateBlockDefinition(
                        input,
                        output,
                        pk.getBlockDefinition()
                    )
                );
            }
            if (pk.getItemInHand() != null) {
                pk.setItemInHand(
                    TypeConverter.translateItemData(
                        input,
                        output,
                        pk.getItemInHand()
                    )
                );
            }
        } else if (packet instanceof MobEquipmentPacket pk) {
            pk.setItem(
                TypeConverter.translateItemData(input, output, pk.getItem())
            );
        } else if (packet instanceof MobArmorEquipmentPacket pk) {
            if (pk.getBody() != null) {
                pk.setBody(
                    TypeConverter.translateItemData(input, output, pk.getBody())
                );
            }
            pk.setChestplate(
                TypeConverter.translateItemData(
                    input,
                    output,
                    pk.getChestplate()
                )
            );
            pk.setHelmet(
                TypeConverter.translateItemData(input, output, pk.getHelmet())
            );
            pk.setBoots(
                TypeConverter.translateItemData(input, output, pk.getBoots())
            );
            pk.setLeggings(
                TypeConverter.translateItemData(input, output, pk.getLeggings())
            );
        } else if (packet instanceof AddPlayerPacket pk) {
            pk.setHand(
                TypeConverter.translateItemData(input, output, pk.getHand())
            );
        }
    }

    @SuppressWarnings("unused")
    private static RecipeData translateRecipeData(
        int input,
        int output,
        RecipeData data
    ) {
        if (data instanceof ShapelessRecipeData shapeless) {
            var newIngredients = shapeless
                .getIngredients()
                .stream()
                .map(ingredient ->
                    translateItemDescriptorWithCount(input, output, ingredient)
                )
                .filter(Objects::nonNull)
                .toList();
            shapeless.getIngredients().clear();
            shapeless.getIngredients().addAll(newIngredients);
            var newResults = shapeless
                .getResults()
                .stream()
                .map(result ->
                    TypeConverter.translateItemData(input, output, result)
                )
                .filter(Objects::nonNull)
                .toList();
            shapeless.getResults().clear();
            shapeless.getResults().addAll(newResults);
            return shapeless;
        }
        if (data instanceof FurnaceRecipeData furnace) {
            return FurnaceRecipeData.of(
                furnace.getType(),
                furnace.getInputId(),
                furnace.getInputData(),
                furnace.getResult(),
                furnace.getTag()
            );
        } else if (data instanceof MultiRecipeData multi) {
            return multi;
        } else if (data instanceof ShapedRecipeData shaped) {
            var newIngredients = shaped
                .getIngredients()
                .stream()
                .map(ingredient ->
                    translateItemDescriptorWithCount(input, output, ingredient)
                )
                .filter(Objects::nonNull)
                .toList();
            shaped.getIngredients().clear();
            shaped.getIngredients().addAll(newIngredients);
            var newResults = shaped
                .getResults()
                .stream()
                .map(result ->
                    TypeConverter.translateItemData(input, output, result)
                )
                .filter(Objects::nonNull)
                .toList();
            shaped.getResults().clear();
            shaped.getResults().addAll(newResults);
            return shaped;
        } else if (
            data instanceof SmithingTransformRecipeData smithingTransform
        ) {
            return SmithingTransformRecipeData.of(
                smithingTransform.getId(),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTransform.getTemplate()
                ),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTransform.getBase()
                ),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTransform.getAddition()
                ),
                TypeConverter.translateItemData(
                    input,
                    output,
                    smithingTransform.getResult()
                ),
                smithingTransform.getTag(),
                smithingTransform.getNetId()
            );
        } else if (data instanceof SmithingTrimRecipeData smithingTrim) {
            return SmithingTrimRecipeData.of(
                smithingTrim.getId(),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTrim.getTemplate()
                ),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTrim.getBase()
                ),
                translateItemDescriptorWithCount(
                    input,
                    output,
                    smithingTrim.getAddition()
                ),
                smithingTrim.getTag(),
                smithingTrim.getNetId()
            );
        }
        throw new IllegalArgumentException(
            "Unknown recipe type " + data.getType()
        );
    }

    private static ItemDescriptorWithCount translateItemDescriptorWithCount(
        int input,
        int output,
        ItemDescriptorWithCount descriptor
    ) {
        var rewritten = TypeConverter.translateItemDescriptor(
            input,
            output,
            descriptor.getDescriptor()
        );
        if (rewritten == null) {
            return null;
        }
        return new ItemDescriptorWithCount(rewritten, descriptor.getCount());
    }
}
