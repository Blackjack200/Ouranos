package com.blackjack200.ouranos.network.cache;

import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import com.nukkitx.protocol.bedrock.packet.BiomeDefinitionListPacket;
import lombok.SneakyThrows;
import lombok.val;

import java.io.IOException;
import java.io.InputStream;

public class StaticPackets {
    private static StaticPackets instance;
    static{
        instance = new StaticPackets();
    }

    public static StaticPackets getInstance() {
        return instance;
    }

    private InputStream getResource(int protocolId, String file) {
        return getClass().getClassLoader().getResourceAsStream("vanilla/v" + protocolId + "/" + file);
    }

    @SneakyThrows
    public AvailableEntityIdentifiersPacket getActorIdsPacket(int protocolId) {
        val pk = new AvailableEntityIdentifiersPacket();
        pk.setIdentifiers(this.readMap(protocolId, "entity_identifiers.nbt"));
        return pk;
    }

    @SneakyThrows
    public BiomeDefinitionListPacket biomeDefinition(int protocolId) {
        val pk = new BiomeDefinitionListPacket();
        pk.setDefinitions(this.readMap(protocolId, "biome_definitions.nbt"));
        return pk;
    }

    private NbtMap readMap(int protocolId, String file) throws IOException {
        return (NbtMap) NbtUtils.createNetworkReader(getResource(protocolId, file)).readTag();
    }
}
