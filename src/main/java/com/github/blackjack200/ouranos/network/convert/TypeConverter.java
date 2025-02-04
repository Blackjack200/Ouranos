package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.item.BlockItemIdMap;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import io.netty.buffer.ByteBuf;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;

@Log4j2
@UtilityClass
public class TypeConverter {
    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull() || !itemData.isValid()) {
            return itemData;
        }

        //downgrade item type
        var def = itemData.getDefinition();
        var data = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(def.getIdentifier(), itemData.getDamage());
        var i = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(data[0].toString(), (Integer) data[1]);

        String newStringId = i[0].toString();
        var newMeta = (Integer) i[1];
        //log.info("old_id={}:{} new_id={}:{}", itemData.getDefinition().getIdentifier(), itemData.getDamage(), newStringId, newMeta);

        var networkId = ItemTypeDictionary.getInstance(output).fromStringId(newStringId);
        if (networkId == null) {
            //log.error("Unknown glk type {}", newStringId);
            return null;
        }

        //downgrade block runtime id
        var bid = BlockItemIdMap.getInstance().lookupItemId(output, newStringId);
        if (bid != null && !bid.equals(newStringId)) {
            log.debug("Inconsistent item id map found for {}=>{}", newStringId, bid);
            //newStringId = bid;
        }

        var builder = itemData.toBuilder();
        builder.definition(new SimpleItemDefinition(newStringId, networkId, false))
                .damage(newMeta);

        if (BlockItemIdMap.getInstance().lookupItemId(output, newStringId) != null && itemData.getBlockDefinition() != null) {
            int trans = translateBlockRuntimeId(input, output, itemData.getBlockDefinition().getRuntimeId());
            builder.blockDefinition(new SimpleBlockDefinition(trans));
        }

        return builder.build();
    }

    public void rewriteFullChunk(int input, int output, ByteBuf from, ByteBuf to, int sections) throws ChunkRewriteException {
        for (var section = 0; section < sections; section++) {
            if (rewriteSubChunk(input, output, from, to)) {
                return;
            }
        }
        int remaining = from.capacity() - from.readerIndex();
        if (remaining > 0) {
            //TODO: implement biome data rewriting
            //TODO: implement block entities data rewriting
            to.writeBytes(from);
        }
    }

    public static boolean rewriteSubChunk(int input, int output, ByteBuf from, ByteBuf to) throws ChunkRewriteException {
        var version = from.readUnsignedByte();
        to.writeByte(version);
        switch (version) {
            case 0, 4, 139 -> {
                to.writeBytes(from);
                return true;
            }
            case 8, 9 -> { // New form chunk, baked-in palette
                var storageCount = from.readUnsignedByte();
                to.writeByte(storageCount);
                if (version == 9) {
                    to.writeByte(from.readUnsignedByte());//what ??? uint8(index + (c.range[0] >> 4))
                }
                for (var storage = 0; storage < storageCount; storage++) {
                    PaletteStorage.translatePaletteStorage(input, output, from, to, TypeConverter::translateBlockRuntimeId);
                }
            }
            default -> { // Unsupported
                throw new ChunkRewriteException("ChunkDataRewrite: Unknown subchunk format " + version);
            }
        }
        return false;
    }

    public int translateBlockRuntimeId(int input, int output, int blockRuntimeId) {
        val inputDict = BlockStateDictionary.getInstance(input);
        val outputDict = BlockStateDictionary.getInstance(output);

        val stateHash = inputDict.toLatestStateHash(blockRuntimeId);

        if (stateHash == null) {
            log.error("unknown block runtime id {}", blockRuntimeId);
            return outputDict.getFallbackRuntimeId();
        }

        var translated = outputDict.toRuntimeId(stateHash);
        if (translated == null) {
            //TODO HACK for heads and skulls, this is not a proper way to translate them. currently unusable
            var anyState = inputDict.lookupStateFromStateHash(stateHash);
            if (anyState != null && (anyState.name().endsWith("_head") || anyState.name().endsWith("_skull"))) {
                Integer skullHash = inputDict.lookupStateIdFromData("minecraft:skeleton_skull", anyState.rawState());
                if (skullHash != null) {
                    Integer rtId = outputDict.toRuntimeId(skullHash);
                    if (rtId != null) {
                        return rtId;
                    }
                }
            }
            return outputDict.getFallbackRuntimeId();
        }
        return translated;
    }

    public BlockDefinition translateBlockDefinition(int input, int output, BlockDefinition definition) {
        return new SimpleBlockDefinition(translateBlockRuntimeId(input, output, definition.getRuntimeId()));
    }

    public ItemDescriptor translateItemDescriptor(int input, int output, ItemDescriptor descriptor) {
        if (descriptor instanceof ComplexAliasDescriptor d) {
            return d;
        } else if (descriptor instanceof DefaultDescriptor d) {
            var itemData = translateItemData(input, output, ItemData.builder().count(1).damage(d.getAuxValue()).definition(d.getItemId()).build());
            if (itemData == null) {
                return InvalidDescriptor.INSTANCE;
            }
            return new DefaultDescriptor(itemData.getDefinition(), itemData.getDamage());
        } else if (descriptor instanceof DeferredDescriptor d) {
            var newData = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(d.getFullName(), d.getAuxValue());
            var downgraded = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(newData[0].toString(), (Integer) newData[1]);
            var newStringId = downgraded[0].toString();
            var newMeta = (Integer) downgraded[1];
            var typ = ItemTypeDictionary.getInstance(output).getEntries().get(newStringId);
            //TODO
            return new DefaultDescriptor(new SimpleItemDefinition(newStringId, typ.runtime_id(), typ.component_based()), newMeta);
        } else if (descriptor instanceof InvalidDescriptor d) {
            //noop
        } else if (descriptor instanceof ItemTagDescriptor d) {
            //TODO
            return d;
        } else if (descriptor instanceof MolangDescriptor d) {
            //TODO
            return d;
        }
        //log.error("unknown descriptor {}", descriptor);
        return InvalidDescriptor.INSTANCE;
    }

    public static CreativeItemData translateCreativeItemData(int input, int output, CreativeItemData itemData) {
        var item = translateItemData(input, output, itemData.getItem());
        if (item == null) {
            return null;
        }
        return new CreativeItemData(item, itemData.getNetId(), itemData.getGroupId());
    }
}
