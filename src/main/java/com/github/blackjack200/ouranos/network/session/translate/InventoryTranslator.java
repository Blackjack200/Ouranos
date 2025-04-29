package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.data.inventory.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class InventoryTranslator {
    public static void rewriteInventory(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (fromServer) {
            handleServerside(input, output, player, p, list);
            return;
        }
        if (p instanceof InventoryTransactionPacket pk) {
            if (pk.getActions().size() == 1) {
                var a = pk.getActions().get(0);
                //TODO
            } else if (pk.getActions().size() == 2) {
                var a = pk.getActions().get(0);
                var b = pk.getActions().get(1);

                var newPk = new ItemStackRequestPacket();
                var aInv = parseContainerId(a.getSource().getContainerId());
                var bInv = parseContainerId(b.getSource().getContainerId());

                if (a.getSource().getType() == InventorySource.Type.CONTAINER && b.getSource().getType() == InventorySource.Type.CONTAINER) {
                    var aOld = player.inventory.inventories.get(a.getSource().getContainerId()).get(a.getSlot());
                    var bOld = player.inventory.inventories.get(b.getSource().getContainerId()).get(b.getSlot());
                    newPk.getRequests().add(new ItemStackRequest(114514, new ItemStackRequestAction[]{
                            new SwapAction(
                                    new ItemStackRequestSlotData(aInv, a.getSlot(), aOld.getNetId(), new FullContainerName(aInv, 0)),
                                    new ItemStackRequestSlotData(bInv, b.getSlot(), bOld.getNetId(), new FullContainerName(bInv, 0))
                            )
                    }, new String[]{}));

                    player.inventory.xa.put(114514, () -> {
                        player.inventory.inventories.get(a.getSource().getContainerId()).set(a.getSlot(), bOld);
                        player.inventory.inventories.get(b.getSource().getContainerId()).set(b.getSlot(), aOld);
                    });

                    list.clear();
                    list.add(newPk);
                }
            }
        }
    }

    private static void handleServerside(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (p instanceof CreativeContentPacket pk) {
            if (output < Bedrock_v407.CODEC.getProtocolVersion()) {
                var newPk = new InventoryContentPacket();
                newPk.setContainerId(ContainerId.CREATIVE);
                List<ItemData> items = pk.getContents().stream().map(CreativeItemData::getItem).filter(i -> i.getDefinition().getVersion().equals(ItemVersion.LEGACY)).collect(Collectors.toList());
                newPk.setContents(items);
                list.clear();
                list.add(newPk);
            }
        } else if (p instanceof InventoryContentPacket pk) {
            player.inventory.inventories.put(pk.getContainerId(), new ArrayList<>(pk.getContents()));
        } else if (p instanceof InventorySlotPacket pk) {
            player.inventory.inventories.putIfAbsent(pk.getContainerId(), new ArrayList<>());
            player.inventory.inventories.get(pk.getContainerId()).set(pk.getSlot(), pk.getItem());
        } else if (p instanceof MobEquipmentPacket pk) {
            player.inventory.inventories.putIfAbsent(pk.getContainerId(), new ArrayList<>());
            player.inventory.inventories.get(pk.getContainerId()).set(pk.getInventorySlot(), pk.getItem());
        } else if (p instanceof ItemStackResponsePacket pk) {
            for (var entry : pk.getEntries()) {
                var xa = player.inventory.xa.get(entry.getRequestId());
                player.inventory.xa.remove(entry.getRequestId());
                if (entry.getResult() == ItemStackResponseStatus.OK) {
                    if (xa != null) {
                        xa.run();
                    }
                } else {
                    player.inventory.inventories.forEach((containerId, contents) -> {
                        var pp = new InventoryContentPacket();
                        pp.setContainerId(containerId);
                        pp.setContents(contents);
                        list.add(pp);
                    });
                    break;
                }
            }
        }
    }

    private static ContainerSlotType parseContainerId(int containerId) {
        switch (containerId) {
            case ContainerId.NONE:
                return ContainerSlotType.UNKNOWN;
            case ContainerId.INVENTORY:
                return ContainerSlotType.INVENTORY;
            case ContainerId.HOTBAR:
                return ContainerSlotType.HOTBAR;
            case ContainerId.ARMOR:
                return ContainerSlotType.ARMOR;
            case ContainerId.OFFHAND:
                return ContainerSlotType.OFFHAND;
            case ContainerId.UI:
                return ContainerSlotType.CURSOR;
            case ContainerId.BEACON:
                return ContainerSlotType.BEACON_PAYMENT;
            case ContainerId.ENCHANT_INPUT:
                return ContainerSlotType.ENCHANTING_INPUT;
            case ContainerId.ENCHANT_OUTPUT:
                return ContainerSlotType.ENCHANTING_MATERIAL;
            default:
                log.error("Unknown container id: {}", containerId);
        }
        return ContainerSlotType.UNKNOWN;
    }
}
