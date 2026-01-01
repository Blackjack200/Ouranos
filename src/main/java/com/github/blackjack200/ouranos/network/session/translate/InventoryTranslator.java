package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.data.inventory.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class InventoryTranslator {
    private static final int UI_CONTAINER_SIZE = 51;
    private static final ContainerSlotType[] UI_SLOT_TYPES = new ContainerSlotType[UI_CONTAINER_SIZE];
    private static final EnumSet<InventorySource.Type> CONTAINER_SOURCES = EnumSet.of(
            InventorySource.Type.CONTAINER,
            InventorySource.Type.GLOBAL,
            InventorySource.Type.UNTRACKED_INTERACTION_UI,
            InventorySource.Type.NON_IMPLEMENTED_TODO
    );

    static {
        for (int i = 0; i < UI_CONTAINER_SIZE; i++) {
            UI_SLOT_TYPES[i] = ContainerSlotType.UNKNOWN;
        }
        UI_SLOT_TYPES[0] = ContainerSlotType.CURSOR;
        UI_SLOT_TYPES[1] = ContainerSlotType.ANVIL_INPUT;
        UI_SLOT_TYPES[2] = ContainerSlotType.ANVIL_MATERIAL;
        UI_SLOT_TYPES[3] = ContainerSlotType.STONECUTTER_INPUT;
        UI_SLOT_TYPES[4] = ContainerSlotType.TRADE2_INGREDIENT_1;
        UI_SLOT_TYPES[5] = ContainerSlotType.TRADE2_INGREDIENT_2;
        UI_SLOT_TYPES[6] = ContainerSlotType.TRADE_INGREDIENT_1;
        UI_SLOT_TYPES[7] = ContainerSlotType.TRADE_INGREDIENT_2;
        UI_SLOT_TYPES[8] = ContainerSlotType.MATERIAL_REDUCER_INPUT;
        UI_SLOT_TYPES[9] = ContainerSlotType.LOOM_INPUT;
        UI_SLOT_TYPES[10] = ContainerSlotType.LOOM_DYE;
        UI_SLOT_TYPES[11] = ContainerSlotType.LOOM_MATERIAL;
        UI_SLOT_TYPES[12] = ContainerSlotType.CARTOGRAPHY_INPUT;
        UI_SLOT_TYPES[13] = ContainerSlotType.CARTOGRAPHY_ADDITIONAL;
        UI_SLOT_TYPES[14] = ContainerSlotType.ENCHANTING_INPUT;
        UI_SLOT_TYPES[15] = ContainerSlotType.ENCHANTING_MATERIAL;
        UI_SLOT_TYPES[16] = ContainerSlotType.GRINDSTONE_INPUT;
        UI_SLOT_TYPES[17] = ContainerSlotType.GRINDSTONE_ADDITIONAL;
        for (int i = 18; i <= 26; i++) {
            UI_SLOT_TYPES[i] = ContainerSlotType.COMPOUND_CREATOR_INPUT;
        }
        UI_SLOT_TYPES[27] = ContainerSlotType.BEACON_PAYMENT;
        for (int i = 28; i <= 31; i++) {
            UI_SLOT_TYPES[i] = ContainerSlotType.CRAFTING_INPUT;
        }
        for (int i = 32; i <= 40; i++) {
            UI_SLOT_TYPES[i] = ContainerSlotType.CRAFTING_INPUT;
        }
        for (int i = 41; i <= 49; i++) {
            UI_SLOT_TYPES[i] = ContainerSlotType.MATERIAL_REDUCER_OUTPUT;
        }
        UI_SLOT_TYPES[50] = ContainerSlotType.CREATED_OUTPUT;
    }

    public static void rewriteInventory(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (player.getDownstreamProtocolId() >= Bedrock_v407.CODEC.getProtocolVersion()) {
            return;
        }
        if (!player.isServerAuthoritativeInventories()) {
            return;
        }
        if (fromServer) {
            handleServerside(input, output, player, p, list);
            return;
        }
        if (p instanceof InventoryTransactionPacket pk) {
            if (pk.getTransactionType() != InventoryTransactionType.NORMAL) {
                return;
            }
            handleClientSideTransaction(player, pk, list);
        }
    }

    @SuppressWarnings("deprecation")
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
            var inv = player.inventory.inventories.get(pk.getContainerId());
            while (inv.size() <= pk.getSlot()) {
                inv.add(ItemData.AIR);
            }
            inv.set(pk.getSlot(), pk.getItem());
        } else if (p instanceof MobEquipmentPacket pk) {
            player.inventory.inventories.putIfAbsent(pk.getContainerId(), new ArrayList<>());
            var inv = player.inventory.inventories.get(pk.getContainerId());
            while (inv.size() <= pk.getInventorySlot()) {
                inv.add(ItemData.AIR);
            }
            inv.set(pk.getInventorySlot(), pk.getItem());
        } else if (p instanceof ItemStackResponsePacket pk) {
            boolean resend = false;
            for (var entry : pk.getEntries()) {
                var xa = player.inventory.xa.get(entry.getRequestId());
                player.inventory.xa.remove(entry.getRequestId());
                if (entry.getResult() == ItemStackResponseStatus.OK) {
                    if (xa != null) {
                        xa.accept(entry.getContainers());
                    }
                    for (var slot : entry.getContainers()) {
                        var id = resolveContainerId(player, slot.getContainerName().getContainer());
                        var container = player.inventory.inventories.get(id);
                        if (container != null) {
                            for (var item : slot.getItems()) {
                                int index = mapRequestSlotToInternal(slot.getContainerName().getContainer(), item.getSlot(), id);
                                if (index < 0 || index >= container.size()) {
                                    continue;
                                }
                                container.set(index, container.get(index).toBuilder().count(item.getCount()).damage(item.getDurabilityCorrection()).usingNetId(true).netId(item.getStackNetworkId()).build());
                            }
                        }
                    }
                } else {
                    resend = true;
                    break;
                }
            }
            // The downstream client is legacy and doesn't understand stack response packets.
            // We rely on inventory content/slot packets to keep it in sync.
            list.clear();
            if (resend) {
                player.inventory.inventories.forEach((containerId, contents) -> {
                    var pp = new InventoryContentPacket();
                    pp.setContainerId(containerId);
                    pp.setContents(contents);
                    list.add(pp);
                });
            }
        }
    }

    private static void handleClientSideTransaction(OuranosProxySession player, InventoryTransactionPacket pk, Collection<BedrockPacket> list) {
        List<InventoryActionData> containerActions = new ArrayList<>();
        boolean wantsLegacyResync = !pk.getLegacySlots().isEmpty();
        boolean hasDropWorldAction = false;
        int worldDropCount = 0;
        for (var action : pk.getActions()) {
            if (action.getSource().getType() == InventorySource.Type.WORLD_INTERACTION) {
                if (action.getSource().getFlag() == InventorySource.Flag.DROP_ITEM) {
                    hasDropWorldAction = true;
                    worldDropCount += Math.max(0, action.getToItem().getCount());
                }
                continue;
            }
            if (CONTAINER_SOURCES.contains(action.getSource().getType())) {
                // Filter out no-op actions. Legacy clients may spam these for slot sync or UI noise.
                if (sameStackExact(action.getFromItem(), action.getToItem())) {
                    continue;
                }
                containerActions.add(action);
            }
        }

        if (containerActions.isEmpty()) {
            // If the client explicitly requests legacy slot resync, forward as a mismatch so the server resends
            // inventories without trying to verify a NORMAL transaction (Dragonfly treats NORMAL as drop-only).
            if (wantsLegacyResync || hasDropWorldAction) {
                rewriteToMismatch(pk, list);
            } else {
                list.clear();
            }
            return;
        }

        List<TxSlotChange> changes = new ArrayList<>(containerActions.size());
        Map<SlotKey, ResolvedSlot> slotMeta = new HashMap<>(containerActions.size());
        Map<SlotKey, ItemData> slotCached = new HashMap<>(containerActions.size());
        Map<SlotKey, ItemData> slotState = new HashMap<>(containerActions.size());
        Map<SlotKey, Integer> slotOccurrences = new HashMap<>(containerActions.size());
        boolean hasMeaningfulChange = hasDropWorldAction && worldDropCount > 0;

        for (var action : containerActions) {
            var resolved = resolveSlot(player, action.getSource(), action.getSlot());
            if (resolved == null) {
                rewriteToMismatch(pk, list);
                return;
            }

            SlotKey slotKey = new SlotKey(resolved.container, resolved.requestSlot);
            slotMeta.putIfAbsent(slotKey, resolved);

            ItemData cached = slotCached.get(slotKey);
            if (cached == null) {
                cached = getCachedItem(player, resolved);
                slotCached.put(slotKey, cached);
            }

            ItemData current = slotState.get(slotKey);
            if (current == null) {
                current = action.getFromItem();
            }

            ItemData from = action.getFromItem();
            // If a slot is mutated multiple times in a single transaction, older clients may omit the intermediate
            // old item. We can reconstruct it from the previous step in the same request.
            if (isEmpty(from) && !isEmpty(current)) {
                from = current;
            }
            ItemData to = action.getToItem();

            changes.add(new TxSlotChange(resolved, cached, from, to));
            slotState.put(slotKey, to);
            slotOccurrences.put(slotKey, slotOccurrences.getOrDefault(slotKey, 0) + 1);
            hasMeaningfulChange |= !sameStackExact(from, to);
        }

        if (!hasMeaningfulChange && !wantsLegacyResync) {
            list.clear();
            return;
        }

        int requestId = player.inventory.nextRequestId();
        var requestActions = new ArrayList<ItemStackRequestAction>(changes.size() + 2);
        var mutatedSlots = new HashSet<SlotKey>();
        var cacheUpdates = new HashMap<CacheKey, ItemData>(changes.size());

        for (var entry : slotMeta.entrySet()) {
            ResolvedSlot resolved = entry.getValue();
            ItemData cached = slotCached.get(entry.getKey());
            ItemData to = slotState.get(entry.getKey());
            if (!sameStackExact(cached, to)) {
                cacheUpdates.put(new CacheKey(resolved.cacheContainerId, resolved.cacheSlot), to);
            }
        }

        boolean[] used = new boolean[changes.size()];
        for (int i = 0; i < changes.size(); i++) {
            if (used[i]) {
                continue;
            }
            var left = changes.get(i);
            if (sameStackExact(left.fromItem, left.toItem)) {
                used[i] = true;
                continue;
            }
            SlotKey leftKey = new SlotKey(left.resolved.container, left.resolved.requestSlot);
            if (slotOccurrences.getOrDefault(leftKey, 0) > 1) {
                continue;
            }
            for (int j = i + 1; j < changes.size(); j++) {
                if (used[j]) {
                    continue;
                }
                var right = changes.get(j);
                SlotKey rightKey = new SlotKey(right.resolved.container, right.resolved.requestSlot);
                if (slotOccurrences.getOrDefault(rightKey, 0) > 1) {
                    continue;
                }
                if (isSwap(left, right)) {
                    var leftSlot = toSlotData(left.resolved, left.cachedItem, requestId, mutatedSlots);
                    var rightSlot = toSlotData(right.resolved, right.cachedItem, requestId, mutatedSlots);
                    requestActions.add(new SwapAction(leftSlot, rightSlot));
                    mutatedSlots.add(new SlotKey(left.resolved.container, left.resolved.requestSlot));
                    mutatedSlots.add(new SlotKey(right.resolved.container, right.resolved.requestSlot));
                    used[i] = true;
                    used[j] = true;
                    break;
                }
            }
        }

        var supplies = new HashMap<ItemKey, ArrayDeque<SlotAmount>>();
        var demands = new HashMap<ItemKey, ArrayDeque<SlotAmount>>();
        var moves = new ArrayList<MoveOp>(changes.size());

        java.util.function.Consumer<ItemKey> match = (key) -> {
            ArrayDeque<SlotAmount> s = supplies.get(key);
            ArrayDeque<SlotAmount> d = demands.get(key);
            if (s == null || d == null) {
                return;
            }
            while (!s.isEmpty() && !d.isEmpty()) {
                SlotAmount supply = s.peekFirst();
                SlotAmount demand = d.peekFirst();
                int move = Math.min(supply.remaining, demand.remaining);

                SlotKey supplyKey = new SlotKey(supply.change.resolved.container, supply.change.resolved.requestSlot);
                SlotKey demandKey = new SlotKey(demand.change.resolved.container, demand.change.resolved.requestSlot);
                if (move > 0 && !supplyKey.equals(demandKey)) {
                    moves.add(new MoveOp(supply.change, demand.change, move));
                }

                supply.remaining -= move;
                demand.remaining -= move;
                if (supply.remaining == 0) {
                    s.pollFirst();
                }
                if (demand.remaining == 0) {
                    d.pollFirst();
                }
            }
        };

        java.util.function.BiConsumer<ItemKey, SlotAmount> addSupply = (key, supply) -> {
            if (supply.remaining <= 0) {
                return;
            }
            supplies.computeIfAbsent(key, $ -> new ArrayDeque<>()).addLast(supply);
            match.accept(key);
        };
        java.util.function.BiConsumer<ItemKey, SlotAmount> addDemand = (key, demand) -> {
            if (demand.remaining <= 0) {
                return;
            }
            demands.computeIfAbsent(key, $ -> new ArrayDeque<>()).addLast(demand);
            match.accept(key);
        };

        for (int i = 0; i < changes.size(); i++) {
            if (used[i]) {
                continue;
            }
            var change = changes.get(i);
            var from = change.fromItem;
            var to = change.toItem;
            if (sameStackExact(from, to)) {
                continue;
            }

            if (sameItemKind(from, to)) {
                int delta = safeCount(to) - safeCount(from);
                if (delta < 0) {
                    addSupply.accept(itemKey(from), new SlotAmount(change, -delta));
                } else if (delta > 0) {
                    addDemand.accept(itemKey(to), new SlotAmount(change, delta));
                }
                continue;
            }

            if (!isEmpty(from)) {
                addSupply.accept(itemKey(from), new SlotAmount(change, safeCount(from)));
            }
            if (!isEmpty(to)) {
                addDemand.accept(itemKey(to), new SlotAmount(change, safeCount(to)));
            }
        }

        for (var move : moves) {
            var source = move.source.resolved;
            var dest = move.dest.resolved;
            var sourceSlot = toSlotData(source, move.source.cachedItem, requestId, mutatedSlots);
            var destSlot = toSlotData(dest, move.dest.cachedItem, requestId, mutatedSlots);
            requestActions.add(new TakeAction(move.count, sourceSlot, destSlot));
            mutatedSlots.add(new SlotKey(source.container, source.requestSlot));
            mutatedSlots.add(new SlotKey(dest.container, dest.requestSlot));
        }

        // Any remaining supplies can only be represented as a drop in a NORMAL legacy transaction.
        int totalRemainingSupply = 0;
        for (var entry : supplies.entrySet()) {
            for (var supply : entry.getValue()) {
                totalRemainingSupply += supply.remaining;
            }
        }
        int totalRemainingDemand = 0;
        for (var entry : demands.entrySet()) {
            for (var demand : entry.getValue()) {
                totalRemainingDemand += demand.remaining;
            }
        }
        if (totalRemainingDemand != 0) {
            rewriteToMismatch(pk, list);
            return;
        }
        if (totalRemainingSupply != 0) {
            if (!hasDropWorldAction || totalRemainingSupply != worldDropCount) {
                rewriteToMismatch(pk, list);
                return;
            }
            int remainingDrop = worldDropCount;
            for (var entry : supplies.entrySet()) {
                for (var supply : entry.getValue()) {
                    while (supply.remaining > 0) {
                        int drop = Math.min(supply.remaining, remainingDrop);
                        if (drop <= 0) {
                            rewriteToMismatch(pk, list);
                            return;
                        }
                        var src = supply.change.resolved;
                        var srcSlot = toSlotData(src, supply.change.cachedItem, requestId, mutatedSlots);
                        requestActions.add(new DropAction(drop, srcSlot, false));
                        mutatedSlots.add(new SlotKey(src.container, src.requestSlot));
                        supply.remaining -= drop;
                        remainingDrop -= drop;
                    }
                }
            }
            if (remainingDrop != 0) {
                rewriteToMismatch(pk, list);
                return;
            }
        }

        if (requestActions.isEmpty()) {
            if (wantsLegacyResync) {
                rewriteToMismatch(pk, list);
            } else {
                list.clear();
            }
            return;
        }

        var newPk = new ItemStackRequestPacket();
        newPk.getRequests().add(new ItemStackRequest(requestId, requestActions.toArray(new ItemStackRequestAction[0]), new String[]{}));
        player.inventory.xa.put(requestId, (slots) -> applyCacheUpdates(player, cacheUpdates));
        list.clear();
        list.add(newPk);
    }

    private static void rewriteToMismatch(InventoryTransactionPacket pk, Collection<BedrockPacket> list) {
        pk.setTransactionType(InventoryTransactionType.INVENTORY_MISMATCH);
        pk.getActions().clear();
        list.clear();
        list.add(pk);
    }

    private static void applyCacheUpdates(OuranosProxySession player, Map<CacheKey, ItemData> updates) {
        for (var entry : updates.entrySet()) {
            var container = player.inventory.inventories.computeIfAbsent(entry.getKey().containerId, $ -> new ArrayList<>());
            int slot = entry.getKey().slot;
            while (container.size() <= slot) {
                container.add(ItemData.AIR);
            }
            ItemData current = container.get(slot);
            ItemData updated = mergeNetId(current, entry.getValue());
            container.set(slot, updated);
        }
    }

    private static ItemData mergeNetId(ItemData current, ItemData updated) {
        if (current == null) {
            return updated;
        }
        if (updated == null) {
            return current;
        }
        if (!updated.isUsingNetId() && current.isUsingNetId()) {
            return updated.toBuilder().usingNetId(true).netId(current.getNetId()).build();
        }
        if (updated.getNetId() == 0 && current.getNetId() != 0) {
            return updated.toBuilder().usingNetId(true).netId(current.getNetId()).build();
        }
        return updated;
    }

    private static boolean isSwap(TxSlotChange left, TxSlotChange right) {
        if (isEmpty(left.fromItem) || isEmpty(left.toItem) || isEmpty(right.fromItem) || isEmpty(right.toItem)) {
            return false;
        }
        return sameItemKind(left.fromItem, right.toItem)
                && sameItemKind(right.fromItem, left.toItem)
                && left.fromItem.getCount() == right.toItem.getCount()
                && right.fromItem.getCount() == left.toItem.getCount();
    }

    private static boolean sameItemKind(ItemData left, ItemData right) {
        if (isEmpty(left) || isEmpty(right)) {
            return false;
        }
        return left.equals(right, false, true, true);
    }

    private static boolean sameStackExact(ItemData left, ItemData right) {
        if (isEmpty(left)) {
            return isEmpty(right);
        }
        if (isEmpty(right)) {
            return false;
        }
        return left.equals(right, true, true, true);
    }

    private static boolean isEmpty(ItemData item) {
        return item == null || item.isNull() || item.getCount() == 0;
    }

    private static int safeCount(ItemData item) {
        return isEmpty(item) ? 0 : item.getCount();
    }

    private static ItemData getCachedItem(OuranosProxySession player, ResolvedSlot resolved) {
        var container = player.inventory.inventories.get(resolved.cacheContainerId);
        if (container == null || resolved.cacheSlot < 0 || resolved.cacheSlot >= container.size()) {
            return null;
        }
        return container.get(resolved.cacheSlot);
    }

    private static ItemStackRequestSlotData toSlotData(ResolvedSlot resolved, ItemData cachedItem, int requestId, Set<SlotKey> mutatedSlots) {
        SlotKey key = new SlotKey(resolved.container, resolved.requestSlot);
        int stackNetId = mutatedSlots.contains(key) ? requestId : (cachedItem != null ? cachedItem.getNetId() : 0);
        return new ItemStackRequestSlotData(resolved.container, resolved.requestSlot, stackNetId, new FullContainerName(resolved.container, 0));
    }

    private static ResolvedSlot resolveSlot(OuranosProxySession player, InventorySource source, int slot) {
        int containerId = source.getContainerId();
        if (source.getType() == InventorySource.Type.GLOBAL) {
            containerId = ContainerId.INVENTORY;
        }
        if (source.getType() == InventorySource.Type.UNTRACKED_INTERACTION_UI
                || source.getType() == InventorySource.Type.NON_IMPLEMENTED_TODO) {
            containerId = ContainerId.UI;
        }
        if (!CONTAINER_SOURCES.contains(source.getType())) {
            return null;
        }
        if (containerId == ContainerId.UI) {
            var slotType = resolveUiSlotType(slot);
            if (slotType == ContainerSlotType.UNKNOWN) {
                return null;
            }
            Integer openId = findOpenContainerId(player, containerTypeForSlotType(slotType));
            if (openId != null) {
                int cacheSlot = mapRequestSlotToInternal(slotType, slot, openId);
                return new ResolvedSlot(slotType, slot, openId, cacheSlot);
            }
            return new ResolvedSlot(slotType, slot, ContainerId.UI, slot);
        }
        if (containerId < 0) {
            return resolveLegacySlot(player, containerId, slot);
        }
        if (containerId == ContainerId.INVENTORY || containerId == ContainerId.HOTBAR || containerId == ContainerId.FIXED_INVENTORY) {
            // Dragonfly accepts inventory actions through the combined hotbar+inventory container. Using a single
            // container here avoids mismatches when the legacy client reports slot indices that don't cleanly map
            // to separate HOTBAR/INVENTORY containers.
            return new ResolvedSlot(ContainerSlotType.HOTBAR_AND_INVENTORY, slot, ContainerId.INVENTORY, slot);
        }
        if (containerId == ContainerId.ARMOR) {
            return new ResolvedSlot(ContainerSlotType.ARMOR, slot, containerId, slot);
        }
        if (containerId == ContainerId.OFFHAND) {
            return new ResolvedSlot(ContainerSlotType.OFFHAND, slot, containerId, slot);
        }
        if (containerId >= ContainerId.FIRST && containerId <= ContainerId.LAST) {
            var openType = player.openContainers.get((byte) containerId);
            var slotType = resolveSlotTypeForContainerType(openType, slot);
            int requestSlot = mapSlotIndexForContainerType(openType, slot);
            return new ResolvedSlot(slotType, requestSlot, containerId, slot);
        }
        return null;
    }

    private static ResolvedSlot resolveLegacySlot(OuranosProxySession player, int containerId, int slot) {
        switch (containerId) {
            case ContainerId.BEACON -> {
                return resolveLegacyOpenOrUi(player, ContainerType.BEACON, ContainerSlotType.BEACON_PAYMENT, slot, 27);
            }
            case ContainerId.ANVIL_MATERIAL -> {
                return resolveLegacyOpenOrUi(player, ContainerType.ANVIL, ContainerSlotType.ANVIL_MATERIAL, slot, 2);
            }
            case ContainerId.ANVIL_RESULT, ContainerId.ANVIL_OUTPUT -> {
                return resolveLegacyOpenOrUi(player, ContainerType.ANVIL, ContainerSlotType.ANVIL_RESULT, slot, 3);
            }
            case ContainerId.CRAFTING_RESULT -> {
                return new ResolvedSlot(ContainerSlotType.CREATED_OUTPUT, 50, ContainerId.UI, 50);
            }
            case ContainerId.CRAFTING_ADD_INGREDIENT, ContainerId.CRAFTING_REMOVE_INGREDIENT, ContainerId.CRAFTING_USE_INGREDIENT -> {
                var openWorkbench = findOpenContainerId(player, ContainerType.WORKBENCH);
                if (openWorkbench != null) {
                    int requestSlot = mapSlotIndexForContainerType(ContainerType.WORKBENCH, slot);
                    return new ResolvedSlot(ContainerSlotType.CRAFTING_INPUT, requestSlot, openWorkbench, slot);
                }
                int uiSlot = resolveCraftingInputSlot(player, slot);
                return new ResolvedSlot(ContainerSlotType.CRAFTING_INPUT, uiSlot, ContainerId.UI, uiSlot);
            }
            case ContainerId.CONTAINER_INPUT -> {
                return resolveContainerInput(player, slot);
            }
            case ContainerId.ENCHANT_INPUT -> {
                return resolveLegacyOpenOrUi(player, ContainerType.ENCHANTMENT, ContainerSlotType.ENCHANTING_INPUT, slot, 14);
            }
            case ContainerId.ENCHANT_MATERIAL -> {
                return resolveLegacyOpenOrUi(player, ContainerType.ENCHANTMENT, ContainerSlotType.ENCHANTING_MATERIAL, slot, 15);
            }
            case ContainerId.ENCHANT_OUTPUT -> {
                return resolveLegacyOpenOrUi(player, ContainerType.ENCHANTMENT, ContainerSlotType.ENCHANTING_INPUT, slot, 14);
            }
            case ContainerId.TRADING_INPUT_1 -> {
                return new ResolvedSlot(ContainerSlotType.TRADE_INGREDIENT_1, 6, ContainerId.UI, 6);
            }
            case ContainerId.TRADING_INPUT_2 -> {
                return new ResolvedSlot(ContainerSlotType.TRADE_INGREDIENT_2, 7, ContainerId.UI, 7);
            }
            case ContainerId.TRADING_OUTPUT -> {
                return new ResolvedSlot(ContainerSlotType.CREATED_OUTPUT, 50, ContainerId.UI, 50);
            }
            case ContainerId.TRADING_USE_INPUTS -> {
                return new ResolvedSlot(ContainerSlotType.TRADE_INGREDIENT_1, 6, ContainerId.UI, 6);
            }
            default -> {
                log.error("Unknown container id: {}", containerId);
                return null;
            }
        }
    }

    private static ResolvedSlot resolveLegacyOpenOrUi(OuranosProxySession player, ContainerType type, ContainerSlotType slotType, int slot, int uiSlot) {
        var openId = findOpenContainerId(player, type);
        if (openId != null) {
            int requestSlot = mapSlotIndexForContainerType(type, slot);
            return new ResolvedSlot(slotType, requestSlot, openId, slot);
        }
        return new ResolvedSlot(slotType, uiSlot, ContainerId.UI, uiSlot);
    }

    private static ResolvedSlot resolveContainerInput(OuranosProxySession player, int slot) {
        ContainerType[] priority = new ContainerType[]{
                ContainerType.ANVIL,
                ContainerType.STONECUTTER,
                ContainerType.LOOM,
                ContainerType.CARTOGRAPHY,
                ContainerType.GRINDSTONE,
                ContainerType.ENCHANTMENT,
                ContainerType.SMITHING_TABLE
        };
        for (var type : priority) {
            var openId = findOpenContainerId(player, type);
            if (openId == null) {
                continue;
            }
            ContainerSlotType slotType = switch (type) {
                case ANVIL -> ContainerSlotType.ANVIL_INPUT;
                case STONECUTTER -> ContainerSlotType.STONECUTTER_INPUT;
                case LOOM -> ContainerSlotType.LOOM_INPUT;
                case CARTOGRAPHY -> ContainerSlotType.CARTOGRAPHY_INPUT;
                case GRINDSTONE -> ContainerSlotType.GRINDSTONE_INPUT;
                case ENCHANTMENT -> ContainerSlotType.ENCHANTING_INPUT;
                case SMITHING_TABLE -> ContainerSlotType.SMITHING_TABLE_INPUT;
                default -> ContainerSlotType.CRAFTING_INPUT;
            };
            int requestSlot = mapSlotIndexForContainerType(type, slot);
            return new ResolvedSlot(slotType, requestSlot, openId, slot);
        }

        var openWorkbench = findOpenContainerId(player, ContainerType.WORKBENCH);
        if (openWorkbench != null) {
            int requestSlot = mapSlotIndexForContainerType(ContainerType.WORKBENCH, slot);
            return new ResolvedSlot(ContainerSlotType.CRAFTING_INPUT, requestSlot, openWorkbench, slot);
        }
        int uiSlot = resolveCraftingInputSlot(player, slot);
        return new ResolvedSlot(ContainerSlotType.CRAFTING_INPUT, uiSlot, ContainerId.UI, uiSlot);
    }

    private static Integer findOpenContainerId(OuranosProxySession player, ContainerType type) {
        if (type == null) {
            return null;
        }
        for (var entry : player.openContainers.entrySet()) {
            if (entry.getValue() == type) {
                return (int) entry.getKey();
            }
        }
        return null;
    }

    private static int resolveContainerId(OuranosProxySession player, ContainerSlotType slotType) {
        Integer openId = findOpenContainerId(player, containerTypeForSlotType(slotType));
        if (openId != null) {
            return openId;
        }
        switch (slotType) {
            case INVENTORY, HOTBAR, HOTBAR_AND_INVENTORY -> {
                return ContainerId.INVENTORY;
            }
            case ARMOR -> {
                return ContainerId.ARMOR;
            }
            case OFFHAND -> {
                return ContainerId.OFFHAND;
            }
            case UNKNOWN -> {
                return ContainerId.NONE;
            }
            default -> {
                return ContainerId.UI;
            }
        }
    }

    private static ContainerType containerTypeForSlotType(ContainerSlotType slotType) {
        return switch (slotType) {
            case ANVIL_INPUT, ANVIL_MATERIAL, ANVIL_RESULT -> ContainerType.ANVIL;
            case ENCHANTING_INPUT, ENCHANTING_MATERIAL -> ContainerType.ENCHANTMENT;
            case GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, GRINDSTONE_RESULT -> ContainerType.GRINDSTONE;
            case LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, LOOM_RESULT -> ContainerType.LOOM;
            case STONECUTTER_INPUT, STONECUTTER_RESULT -> ContainerType.STONECUTTER;
            case CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_RESULT -> ContainerType.CARTOGRAPHY;
            case SMITHING_TABLE_INPUT, SMITHING_TABLE_MATERIAL, SMITHING_TABLE_TEMPLATE, SMITHING_TABLE_RESULT -> ContainerType.SMITHING_TABLE;
            case BEACON_PAYMENT -> ContainerType.BEACON;
            case FURNACE_INGREDIENT, FURNACE_FUEL, FURNACE_RESULT -> ContainerType.FURNACE;
            case BLAST_FURNACE_INGREDIENT -> ContainerType.BLAST_FURNACE;
            case SMOKER_INGREDIENT -> ContainerType.SMOKER;
            case BREWING_INPUT, BREWING_RESULT, BREWING_FUEL -> ContainerType.BREWING_STAND;
            case TRADE_INGREDIENT_1, TRADE_INGREDIENT_2, TRADE_RESULT, TRADE2_INGREDIENT_1, TRADE2_INGREDIENT_2, TRADE2_RESULT -> ContainerType.TRADE;
            case LEVEL_ENTITY, BARREL, SHULKER_BOX -> ContainerType.CONTAINER;
            case CRAFTING_INPUT -> ContainerType.WORKBENCH;
            default -> null;
        };
    }

    private static ContainerSlotType resolveSlotTypeForContainerType(ContainerType type, int slot) {
        if (type == null) {
            return ContainerSlotType.LEVEL_ENTITY;
        }
        return switch (type) {
            case WORKBENCH -> ContainerSlotType.CRAFTING_INPUT;
            case FURNACE -> resolveFurnaceSlotType(slot, ContainerSlotType.FURNACE_INGREDIENT);
            case BLAST_FURNACE -> resolveFurnaceSlotType(slot, ContainerSlotType.BLAST_FURNACE_INGREDIENT);
            case SMOKER -> resolveFurnaceSlotType(slot, ContainerSlotType.SMOKER_INGREDIENT);
            case ENCHANTMENT -> slot == 0 ? ContainerSlotType.ENCHANTING_INPUT : ContainerSlotType.ENCHANTING_MATERIAL;
            case BREWING_STAND -> resolveBrewingSlotType(slot);
            case ANVIL -> resolveAnvilSlotType(slot);
            case BEACON -> ContainerSlotType.BEACON_PAYMENT;
            case LOOM -> resolveLoomSlotType(slot);
            case GRINDSTONE -> resolveGrindstoneSlotType(slot);
            case STONECUTTER -> slot == 0 ? ContainerSlotType.STONECUTTER_INPUT : ContainerSlotType.STONECUTTER_RESULT;
            case CARTOGRAPHY -> resolveCartographySlotType(slot);
            case SMITHING_TABLE -> resolveSmithingSlotType(slot);
            case CONTAINER -> ContainerSlotType.LEVEL_ENTITY;
            case HORSE -> ContainerSlotType.HORSE_EQUIP;
            case TRADE -> switch (slot) {
                case 0 -> ContainerSlotType.TRADE_INGREDIENT_1;
                case 1 -> ContainerSlotType.TRADE_INGREDIENT_2;
                case 2 -> ContainerSlotType.TRADE_RESULT;
                default -> ContainerSlotType.TRADE_INGREDIENT_1;
            };
            default -> ContainerSlotType.LEVEL_ENTITY;
        };
    }

    private static ContainerSlotType resolveFurnaceSlotType(int slot, ContainerSlotType ingredientType) {
        return switch (slot) {
            case 0 -> ingredientType;
            case 1 -> ContainerSlotType.FURNACE_FUEL;
            case 2 -> ContainerSlotType.FURNACE_RESULT;
            default -> ingredientType;
        };
    }

    private static ContainerSlotType resolveBrewingSlotType(int slot) {
        if (slot == 0) {
            return ContainerSlotType.BREWING_INPUT;
        }
        if (slot == 4) {
            return ContainerSlotType.BREWING_FUEL;
        }
        return ContainerSlotType.BREWING_RESULT;
    }

    private static ContainerSlotType resolveAnvilSlotType(int slot) {
        return switch (slot) {
            case 0 -> ContainerSlotType.ANVIL_INPUT;
            case 1 -> ContainerSlotType.ANVIL_MATERIAL;
            case 2 -> ContainerSlotType.ANVIL_RESULT;
            default -> ContainerSlotType.ANVIL_INPUT;
        };
    }

    private static ContainerSlotType resolveLoomSlotType(int slot) {
        return switch (slot) {
            case 0 -> ContainerSlotType.LOOM_INPUT;
            case 1 -> ContainerSlotType.LOOM_DYE;
            case 2 -> ContainerSlotType.LOOM_MATERIAL;
            case 3 -> ContainerSlotType.LOOM_RESULT;
            default -> ContainerSlotType.LOOM_INPUT;
        };
    }

    private static ContainerSlotType resolveGrindstoneSlotType(int slot) {
        return switch (slot) {
            case 0 -> ContainerSlotType.GRINDSTONE_INPUT;
            case 1 -> ContainerSlotType.GRINDSTONE_ADDITIONAL;
            case 2 -> ContainerSlotType.GRINDSTONE_RESULT;
            default -> ContainerSlotType.GRINDSTONE_INPUT;
        };
    }

    private static ContainerSlotType resolveCartographySlotType(int slot) {
        return switch (slot) {
            case 0 -> ContainerSlotType.CARTOGRAPHY_INPUT;
            case 1 -> ContainerSlotType.CARTOGRAPHY_ADDITIONAL;
            case 2 -> ContainerSlotType.CARTOGRAPHY_RESULT;
            default -> ContainerSlotType.CARTOGRAPHY_INPUT;
        };
    }

    private static ContainerSlotType resolveSmithingSlotType(int slot) {
        return switch (slot) {
            case 0 -> ContainerSlotType.SMITHING_TABLE_INPUT;
            case 1 -> ContainerSlotType.SMITHING_TABLE_MATERIAL;
            case 2 -> ContainerSlotType.SMITHING_TABLE_TEMPLATE;
            case 3 -> ContainerSlotType.SMITHING_TABLE_RESULT;
            default -> ContainerSlotType.SMITHING_TABLE_INPUT;
        };
    }

    private static int mapSlotIndexForContainerType(ContainerType type, int slot) {
        if (type == null) {
            return slot;
        }
        return switch (type) {
            case ANVIL -> mapSlotIndex(slot, 1, 3);
            case WORKBENCH -> mapSlotIndex(slot, 32, 40);
            case LOOM -> mapSlotIndex(slot, 9, 12);
            case CARTOGRAPHY -> mapSlotIndex(slot, 12, 14);
            case ENCHANTMENT -> mapSlotIndex(slot, 14, 15);
            case GRINDSTONE -> mapSlotIndex(slot, 16, 18);
            case STONECUTTER -> mapSlotIndex(slot, 3, 4);
            case BEACON -> slot == 0 ? 27 : slot;
            case SMITHING_TABLE -> mapSlotIndex(slot, 51, 54);
            default -> slot;
        };
    }

    private static int mapSlotIndex(int slot, int start, int end) {
        int size = end - start + 1;
        if (slot >= 0 && slot < size) {
            return start + slot;
        }
        if (slot >= start && slot <= end) {
            return slot;
        }
        return slot;
    }

    private static int resolveCraftingInputSlot(OuranosProxySession player, int slot) {
        if (slot < 0) {
            return slot;
        }
        boolean workbenchOpen = findOpenContainerId(player, ContainerType.WORKBENCH) != null;
        if (!workbenchOpen && slot <= 3) {
            return 28 + slot;
        }
        return 32 + slot;
    }

    private static ContainerSlotType resolveUiSlotType(int slot) {
        if (slot < 0 || slot >= UI_SLOT_TYPES.length) {
            return ContainerSlotType.UNKNOWN;
        }
        return UI_SLOT_TYPES[slot];
    }

    private static int mapRequestSlotToInternal(ContainerSlotType slotType, int requestSlot, int containerId) {
        if (containerId == ContainerId.UI) {
            return requestSlot;
        }
        return switch (slotType) {
            case CRAFTING_INPUT -> {
                if (requestSlot >= 32 && requestSlot <= 40) {
                    yield requestSlot - 32;
                }
                if (requestSlot >= 28 && requestSlot <= 31) {
                    yield requestSlot - 28;
                }
                yield requestSlot;
            }
            case ANVIL_INPUT, ANVIL_MATERIAL, ANVIL_RESULT -> requestSlot >= 1 && requestSlot <= 3 ? requestSlot - 1 : requestSlot;
            case ENCHANTING_INPUT, ENCHANTING_MATERIAL -> requestSlot >= 14 && requestSlot <= 15 ? requestSlot - 14 : requestSlot;
            case GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, GRINDSTONE_RESULT -> requestSlot >= 16 && requestSlot <= 18 ? requestSlot - 16 : requestSlot;
            case LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, LOOM_RESULT -> requestSlot >= 9 && requestSlot <= 12 ? requestSlot - 9 : requestSlot;
            case CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_RESULT -> requestSlot >= 12 && requestSlot <= 14 ? requestSlot - 12 : requestSlot;
            case STONECUTTER_INPUT, STONECUTTER_RESULT -> requestSlot >= 3 && requestSlot <= 4 ? requestSlot - 3 : requestSlot;
            case BEACON_PAYMENT -> requestSlot == 27 ? 0 : requestSlot;
            case SMITHING_TABLE_INPUT, SMITHING_TABLE_MATERIAL, SMITHING_TABLE_TEMPLATE, SMITHING_TABLE_RESULT -> requestSlot >= 51 && requestSlot <= 54 ? requestSlot - 51 : requestSlot;
            case CREATED_OUTPUT, CRAFTING_OUTPUT -> requestSlot == 50 ? 0 : requestSlot;
            default -> requestSlot;
        };
    }

    private record SlotKey(ContainerSlotType container, int slot) {
    }

    private record CacheKey(int containerId, int slot) {
    }

    private record ResolvedSlot(ContainerSlotType container, int requestSlot, int cacheContainerId, int cacheSlot) {
    }

    private record ItemKey(ItemData normalized) {
    }

    private static ItemKey itemKey(ItemData item) {
        return new ItemKey(item.toBuilder().count(1).usingNetId(false).netId(0).build());
    }

    private static final class SlotAmount {
        private final TxSlotChange change;
        private int remaining;

        private SlotAmount(TxSlotChange change, int remaining) {
            this.change = change;
            this.remaining = remaining;
        }
    }

    private record MoveOp(TxSlotChange source, TxSlotChange dest, int count) {
    }

    private record TxSlotChange(ResolvedSlot resolved, ItemData cachedItem, ItemData fromItem, ItemData toItem) {
    }
}
