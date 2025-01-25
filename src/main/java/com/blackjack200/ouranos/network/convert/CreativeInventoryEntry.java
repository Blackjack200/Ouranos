package com.blackjack200.ouranos.network.convert;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class CreativeInventoryEntry {
    public String name;
    public String block_states;
    public Integer id;
    public int damage;
    public String nbt_b64;

    public ItemData make(int target) {
        ItemTypeInfo entry;
        String na;
        if (this.name != null) {
            entry = ItemTypeDictionary.getInstance().getEntries(target).get(this.name);
            na = this.name;
        } else {
            var stringId = ItemTranslator.getInstance().getAlias(target, LegacyItemIdToStringIdMap.getInstance().fromNumeric(target, this.id));
            entry = ItemTypeDictionary.getInstance().getEntries(target).get(stringId);
            na = stringId;
            if(entry==null){
                return null;
            }
        }
        var newData = ItemData.builder()
                .damage(this.damage)
                .count(1)
                .definition(new SimpleItemDefinition(na, entry.runtime_id, entry.component_based));
        if (this.block_states != null) {
            int translated = RuntimeBlockMapping.getInstance().fromNbt(target, this.name,Base64.getDecoder().wrap(new ByteArrayInputStream(this.block_states.getBytes())));
            newData.blockDefinition(() -> translated);
        }
        Integer translated = RuntimeBlockMapping.getInstance().fromNbt2(target, this.name, NbtMap.EMPTY);
if(translated!=null) {
    newData.blockDefinition(() -> translated);
}
        try {
            if (this.nbt_b64 != null) {
                var reader = new ByteArrayInputStream(this.nbt_b64.getBytes());
                newData.tag((NbtMap) NbtUtils.createReaderLE(Base64.getDecoder().wrap(reader)).readTag());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return newData.build();
    }
}
