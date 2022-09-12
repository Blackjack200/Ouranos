package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.mapping.ItemTranslator;
import com.nukkitx.protocol.bedrock.data.inventory.ContainerMixData;
import com.nukkitx.protocol.bedrock.data.inventory.CraftingData;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.packet.CraftingDataPacket;
import lombok.val;

import java.util.ArrayList;

public class Translate {
    public static CraftingDataPacket translate(int originalProtocolId, int targetProtocolId, CraftingDataPacket pk) {
        val newPk = new CraftingDataPacket();
        pk.getCraftingData().forEach((data) -> {
            int[] out = translateItemStack(originalProtocolId, targetProtocolId, data.getInputId(), data.getInputDamage());
            val inputs = new ArrayList<ItemData>();
            data.getInputs().forEach((d) -> inputs.add(translateItemData(originalProtocolId, targetProtocolId, d)));
            val outputs = new ArrayList<ItemData>();
            data.getOutputs().forEach((d) -> outputs.add(translateItemData(originalProtocolId, targetProtocolId, d)));
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
        });
        pk.getContainerMixData().forEach((data)->{
            /*val newData = new ContainerMixData();
            newPk.getCraftingData().add(newData)*/
        });
        return newPk;
    }

    private static ItemData translateItemData(int originalProtocolId, int targetProtocolId, ItemData d) {
        val item = translateItemStack(originalProtocolId, targetProtocolId, d.getId(), d.getDamage());
        return ItemData.builder()
                .id(item[0])
                .damage(item[1])
                .count(d.getCount())
                .tag(d.getTag())
                .canPlace(d.getCanPlace())
                .canBreak(d.getCanBreak())
                .blockingTicks(d.getBlockingTicks())
                //TODO BlockRuntimeId
                .blockRuntimeId(d.getBlockRuntimeId())
                .usingNetId(false)
                .netId(1).build();
    }

    private static int[] translateItemStack(int originalProtocolId, int targetProtocolId, int itemId, int itemMeta) {
        int[] in = ItemTranslator.getInstance().fromNetworkIdNotNull(originalProtocolId, itemId, itemMeta);
        return ItemTranslator.getInstance().toNetworkId(targetProtocolId, in[0], in[1]);
    }
}
