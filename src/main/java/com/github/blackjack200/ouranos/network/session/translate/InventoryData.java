package com.github.blackjack200.ouranos.network.session.translate;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InventoryData {
    public Map<Integer, List<ItemData>> inventories = new HashMap<>();
    public Map<Integer, Consumer<List<ItemStackResponseContainer>>> xa = new HashMap<>();
}
