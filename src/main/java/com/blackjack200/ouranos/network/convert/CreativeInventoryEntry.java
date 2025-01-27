package com.blackjack200.ouranos.network.convert;

import com.blackjack200.ouranos.network.data.BlockItemIdMap;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Log4j2
public class CreativeInventoryEntry {
    public String name;
    public String block_states;

    public Integer id;
    public int damage;
    public String nbt_b64;

    @SneakyThrows
    public ItemData make(int target) {
        if (this.name != null) {
            val blockName = BlockItemIdMap.getInstance().lookupBlockId(target, this.name);
            if (blockName != null) {
                //log.warn("Found block id {} for name {}", blockName, this.name);
                if (this.damage != 0) {
                    throw new RuntimeException("Meta should not be specified for blockitems");
                }
                if (this.block_states != null) {
                    val x = Base64.getDecoder().wrap(new ByteArrayInputStream(this.block_states.getBytes()));
                    val blockRuntimeId = RuntimeBlockMapping.getInstance().toRuntimeId(target, RuntimeBlockMapping.getInstance().fromNbt(this.name, x));
                    if (blockRuntimeId == null) {
                        return null;
                    }
                    //log.warn("{} Found block id {} for blockid {}", target, blockRuntimeId, this.name);
                }
            } else {
                //log.warn("Found stateless block id {} for name {}", blockName, this.name);
            }
            val builder = ItemData.builder()
                    .definition(new SimpleItemDefinition(this.name, ItemTypeDictionary.getInstance().fromStringId(target, this.name), false))
                    .damage(0)
                    .count(1)
                    .tag(NbtMap.EMPTY)
                    .blockingTicks(0)
                    .usingNetId(false);
            return builder.build();
        }
        return null;
    }
}
