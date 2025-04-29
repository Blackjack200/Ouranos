package com.github.blackjack200.ouranos.network.session.translate;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryData {
    public Map<Integer, List<ItemData>> inventories = new HashMap<>();
    public Map<Integer, Runnable> xa = new HashMap<>();
}
