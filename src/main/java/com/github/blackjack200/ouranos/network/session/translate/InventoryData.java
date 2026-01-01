package com.github.blackjack200.ouranos.network.session.translate;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InventoryData {
    // Bedrock stack requests commonly use negative request IDs. Dragonfly also uses negative values in
    // StackNetworkID as references to request IDs, so we generate negative IDs to allow referencing within
    // a single request.
    private int nextRequestId = -1;
    public Map<Integer, List<ItemData>> inventories = new HashMap<>();
    public Map<Integer, Consumer<List<ItemStackResponseContainer>>> xa = new HashMap<>();

    public int nextRequestId() {
        return nextRequestId--;
    }
}
