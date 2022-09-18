package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.network.mapping.ItemTranslator;
import com.blackjack200.ouranos.network.mapping.ItemTypeDictionary;
import com.blackjack200.ouranos.network.mapping.LegacyBlockIdToStringIdMap;
import com.blackjack200.ouranos.network.mapping.RuntimeBlockMapping;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.data.inventory.ContainerMixData;
import com.nukkitx.protocol.bedrock.data.inventory.CraftingData;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.packet.*;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@Log4j2
public class Translate {
    public static BedrockPacket translate(int originalProtocolId, int targetProtocolId, BedrockPacket p) {
        if (p instanceof PlayStatusPacket) {
            log.info(p);
        }
        if (p instanceof CraftingDataPacket) {
            return translateCraftingData(originalProtocolId, targetProtocolId, (CraftingDataPacket) p);
        }
        if (p instanceof StartGamePacket) {
            val pk = ((StartGamePacket) p);
            val newEntires = new ArrayList<StartGamePacket.ItemEntry>();
            ItemTypeDictionary.getInstance().getEntries(targetProtocolId).forEach((key, val) -> {
                newEntires.add(new StartGamePacket.ItemEntry(key, (short) val.runtime_id, val.component_based));
            });
            pk.setItemEntries(newEntires);
        }
        if (p instanceof InventoryContentPacket) {
            val pk = ((InventoryContentPacket) p);
            val newContents = new ArrayList<ItemData>();
            for (ItemData data : pk.getContents()) {
                newContents.add(translateItemStack(originalProtocolId, targetProtocolId, data));
            }
            pk.setContents(newContents);
        }
        if (p instanceof InventorySlotPacket) {
            val pk = ((InventorySlotPacket) p);
            pk.setItem(translateItemStack(originalProtocolId, targetProtocolId, pk.getItem()));
        }
        if (p instanceof CreativeContentPacket) {
            val pk = (CreativeContentPacket) p;
            val newContents = new ArrayList<ItemData>();
            for (int i = 0; i < pk.getContents().length; i++) {
                newContents.add(translateItemStack(originalProtocolId, targetProtocolId, pk.getContents()[i]));
            }
            pk.setContents(Arrays.stream(pk.getContents()).map((e) -> translateItemStack(originalProtocolId, targetProtocolId, e)).toArray(ItemData[]::new));
            return pk;
        }
        if (p instanceof AvailableCommandsPacket) {
            val pk = ((AvailableCommandsPacket) p);
            return pk;
        }
        if (p instanceof MobEquipmentPacket) {
            var pk = ((MobEquipmentPacket) p);
            pk.setItem(translateItemStack(originalProtocolId, targetProtocolId, pk.getItem()));
            return pk;
        }
        String minecraftVersion = Objects.requireNonNull(ProtocolInfo.getPacketCodec(targetProtocolId)).getMinecraftVersion();
        if (p instanceof ResourcePacksInfoPacket) {
            val pk = ((ResourcePacksInfoPacket) p);
            var newPk = new ResourcePacksInfoPacket();
            pk.getResourcePackInfos().forEach((a) -> {
                newPk.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(
                        a.getPackId(),
                        minecraftVersion,
                        a.getPackSize(),
                        a.getContentKey(),
                        a.getSubPackName(),
                        a.getContentId(),
                        a.isScripting(),
                        a.isRaytracingCapable()
                ));
            });
        }
        if (p instanceof ResourcePackStackPacket) {
            val pk = ((ResourcePackStackPacket) p);
            pk.setGameVersion(minecraftVersion);
        }
        return p;
    }

    private static CraftingDataPacket translateCraftingData(int originalProtocolId, int targetProtocolId, CraftingDataPacket pk) {
        val newPk = new CraftingDataPacket();
        pk.getCraftingData().forEach((data) -> {
            try {
                int[] out = translateItem(originalProtocolId, targetProtocolId, data.getInputId(), data.getInputDamage());
                val inputs = new ArrayList<ItemData>();
                data.getInputs().forEach((d) -> inputs.add(translateItemStack(originalProtocolId, targetProtocolId, d)));
                val outputs = new ArrayList<ItemData>();
                data.getOutputs().forEach((d) -> outputs.add(translateItemStack(originalProtocolId, targetProtocolId, d)));
                newPk.getCraftingData().add(new CraftingData(
                        data.getType(),
                        data.getRecipeId(),
                        data.getWidth(),
                        data.getHeight(),
                        out[0],
                        out[1],
                        inputs,
                        outputs,
                        data.getUuid(),
                        data.getCraftingTag(),
                        data.getPriority()
                ));
            } catch (Throwable ignored) {
            }
        });
        //TODO Material Reducers not used in PM4
        /*pk.getMaterialReducers().forEach((data)->{
            val newData = new MaterialReducer()
        });*/
        pk.getPotionMixData().forEach((data) -> {
            // val newData = new PotionMixData(1, 1, 1, 1, 1, 1);
            //newPk.getPotionMixData().add(newData);
        });

        pk.getContainerMixData().forEach((data) -> {
            try {
                val newData = new ContainerMixData(
                        translateItemNetworkId(originalProtocolId, targetProtocolId, data.getInputId()),
                        translateItemNetworkId(originalProtocolId, targetProtocolId, data.getReagentId()),
                        translateItemNetworkId(originalProtocolId, targetProtocolId, data.getOutputId())
                );

                newPk.getContainerMixData().add(newData);
            } catch (Throwable ignored) {
            }
        });
        return newPk;
    }

    private static ItemData translateItemStack(int originalProtocolId, int targetProtocolId, ItemData oldStack) {
        if (!oldStack.isValid()) {
            return oldStack;
        }
        val item = translateItem(originalProtocolId, targetProtocolId, oldStack.getId(), oldStack.getDamage());
        return ItemData.builder()
                .id(item[0])
                .damage(item[1])
                .count(oldStack.getCount())
                .tag(oldStack.getTag())
                .canPlace(oldStack.getCanPlace())
                .canBreak(oldStack.getCanBreak())
                .blockingTicks(oldStack.getBlockingTicks())
                .blockRuntimeId(translateBlockRuntimeId(originalProtocolId, targetProtocolId, oldStack.getBlockRuntimeId()))
                .usingNetId(oldStack.getNetId() != 0)
                .netId(oldStack.getNetId()).build();
    }

    private static int translateBlockRuntimeId(int originalProtocolId, int targetProtocolId, int blockRuntimeId) {
        if (blockRuntimeId == 0) {
            return 0;
        }
        val internalStateId = RuntimeBlockMapping.getInstance().fromRuntimeId(originalProtocolId, blockRuntimeId);
        return RuntimeBlockMapping.getInstance().toRuntimeId(targetProtocolId, internalStateId);
    }

    private static int[] translateItem(int originalProtocolId, int targetProtocolId, int itemId, int itemMeta) {
        if (itemId == 0) {
            return new int[]{itemId, itemMeta};
        }
        int[] coreData = ItemTranslator.getInstance().fromNetworkIdNotNull(originalProtocolId, itemId, itemMeta);
        return ItemTranslator.getInstance().toNetworkId(targetProtocolId, coreData[0], coreData[1]);
    }

    private static int translateItemNetworkId(int originalProtocolId, int targetProtocolId, int networkId) {
        if (networkId == 0) {
            return networkId;
        }
        int[] data = ItemTranslator.getInstance().fromNetworkIdNotNull(originalProtocolId, networkId, 0);
        return ItemTranslator.getInstance().toNetworkId(targetProtocolId, data[0], data[1])[0];
    }
}
