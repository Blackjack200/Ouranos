package com.blackjack200.ouranos.network.convert;

import com.blackjack200.ouranos.network.data.AbstractMapping;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreativeInventory extends AbstractMapping {
    private static final CreativeInventory instance;
    private Map<Integer, CreativeContentPacket> packets = new HashMap<>();

    static {
        instance = new CreativeInventory();
    }

    public static CreativeInventory getInstance() {
        return instance;
    }

    public CreativeInventory() {
        load("creativeitems.json", (protocolId, rawData) -> {
            List<CreativeInventoryEntry> data = (new Gson()).fromJson(new InputStreamReader(rawData), new TypeToken<List<CreativeInventoryEntry>>() {
            }.getType());
            var pk = new CreativeContentPacket();
            var list = new ArrayList<ItemData>(data.size());
            for (var entry : data) {
                var e = entry.make(protocolId);
                if (e != null) {
                    list.add(e);
                }
            }
            pk.setContents(list.toArray(new ItemData[]{}));
            this.packets.put(protocolId, pk);
        });
    }

    public CreativeContentPacket getPacket(int protocol) {
        return this.packets.get(protocol);
    }
}
