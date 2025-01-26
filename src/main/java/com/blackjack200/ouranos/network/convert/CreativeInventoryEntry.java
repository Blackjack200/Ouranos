package com.blackjack200.ouranos.network.convert;

import com.blackjack200.ouranos.network.data.BlockItemIdMap;
import com.blackjack200.ouranos.network.data.LegacyItemIdToStringIdMap;
import lombok.SneakyThrows;
import lombok.val;
import org.allaymc.updater.item.ItemStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.io.ByteArrayInputStream;
import java.util.Base64;

public class CreativeInventoryEntry {
    public String name;
    public String block_states;

    public Integer id;
    public int damage;
    public String nbt_b64;

    @SneakyThrows
    public ItemData make(int target) {
        Integer blockRuntimeId = null;
        if (this.name != null) {
            var blockName = BlockItemIdMap.getInstance().lookupBlockId(target, this.name);
            if (blockName != null) {
                if (this.damage != 0) {
                    throw new RuntimeException("Meta should not be specified for blockitems");
                }
                if (this.block_states != null) {
                    var x = Base64.getDecoder().wrap(new ByteArrayInputStream(this.block_states.getBytes()));
                    blockRuntimeId = RuntimeBlockMapping.getInstance().toRuntimeId(target, RuntimeBlockMapping.getInstance().fromNbt(this.name, x));
                }
            }
            val builder = ItemData.builder()
                    .definition(new SimpleItemDefinition(this.name, ItemTypeDictionary.getInstance().fromStringId(target, this.name), false))
                    .damage(0)
                    .count(1)
                    .tag(NbtMap.EMPTY)
                    .blockingTicks(0)
                    .usingNetId(false);
            if (blockRuntimeId != null) {
                val rtId = blockRuntimeId;
                builder.blockDefinition(() -> rtId);
            }
            return builder.build();
        } else {
            String lol = LegacyItemIdToStringIdMap.getInstance().fromNumeric(target, this.id);
            if (lol == null) {
                throw new RuntimeException("Meta should not be specified for lol");
            }
            val builder = ItemData.builder()
                    .definition(new SimpleItemDefinition(lol, this.damage, false))
                    .damage(this.damage)
                    .count(1)
                    .tag(NbtMap.EMPTY)
                    .blockingTicks(0)
                    .usingNetId(false);
            if (this.nbt_b64 != null) {
                var reader = new ByteArrayInputStream(this.nbt_b64.getBytes());
                builder.tag((NbtMap) NbtUtils.createReaderLE(Base64.getDecoder().wrap(reader)).readTag());
            }
            return builder.build();
        }
    }
}
