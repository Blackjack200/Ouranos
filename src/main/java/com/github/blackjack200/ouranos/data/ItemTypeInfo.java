package com.github.blackjack200.ouranos.data;

import com.github.blackjack200.ouranos.utils.SimpleVersionedItemDefinition;
import lombok.SneakyThrows;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;

import java.io.ByteArrayInputStream;
import java.util.Base64;

public record ItemTypeInfo(int runtime_id, boolean component_based, int version, String component_nbt) {
    public ItemVersion getVersion() {
        return ItemVersion.from(version);
    }

    @SneakyThrows
    public NbtMap getComponentNbt() {
        if (component_nbt != null) {
            return (NbtMap) NbtUtils.createReaderLE(new ByteArrayInputStream(Base64.getDecoder().decode(component_nbt))).readTag();
        }
        return NbtMap.EMPTY;
    }

    public ItemDefinition toDefinition(String id) {
        return new SimpleVersionedItemDefinition(id, this.runtime_id(), this.getVersion(), this.component_based(), this.getComponentNbt());
    }
}
