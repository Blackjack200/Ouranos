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
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.Objects;

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

        var networkId = ItemTypeDictionary.getInstance(output).fromStringId(newStringId);
        if (networkId == null) {
            //log.error("Unknown glk type {}", newStringId);
            return null;
        }

        //downgrade block runtime id
        var bid = BlockItemIdMap.getInstance().lookupItemId(output, newStringId);
        if (bid != null && !bid.equals(newStringId)) {
            log.debug("Inconsistent item id map found for {}=>{}", newStringId, bid);
            newStringId = bid;
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


    public void rewriteChunkData(int input, int output, ByteBuf from, ByteBuf to, int sections) throws ChunkRewriteException {
        val isNetwork = 1;
        for (var section = 0; section < sections; section++) {
            var version = from.readUnsignedByte();
            to.writeByte(version);
            switch (version) {
                case 0, 4, 139 -> {
                    to.writeBytes(from);
                    return;
                }
                case 8, 9 -> { // New form chunk, baked-in palette
                    var storageCount = from.readUnsignedByte();
                    to.writeByte(storageCount);
                    if (version == 9) {
                        to.writeByte(from.readUnsignedByte());//what ??? uint8(index + (c.range[0] >> 4))
                    }

                    for (var storage = 0; storage < storageCount; storage++) {
                        var blockSize = from.readUnsignedByte() >> 1;
                        if (blockSize == 0x7f) {
                            //no data
                            return;
                        }
                        if (blockSize > 32) {
                            throw new ChunkRewriteException("cannot read paletted storage (size=" + blockSize + "): size too large");
                        }

                        to.writeByte((blockSize << 1) | isNetwork);
                        val uint32Count = uint32s(blockSize);
                        val byteCount = uint32Count * 4;

                        to.writeBytes(from, byteCount);

                        var paletteCount = 1;
                        if (blockSize != 0) {
                            paletteCount = VarInts.readInt(from);
                            if (paletteCount <= 0) {
                                throw new ChunkRewriteException("invalid palette entry count " + paletteCount);
                            }
                        }
                        VarInts.writeInt(to, paletteCount);

                        for (var i = 0; i < paletteCount; i++) {
                            int runtimeId = VarInts.readInt(from);
                            VarInts.writeInt(to, translateBlockRuntimeId(input, output, runtimeId));
                        }
                    }
                }
                default -> { // Unsupported
                    throw new ChunkRewriteException("PEBlockRewrite: Unknown subchunk format " + version);
                }
            }
        }
    }

    private int uint32s(int p) {
        var uint32Count = 0;
        if (p != 0) {
            val indicesPerUint32 = 32 / p;
            uint32Count = 4096 / indicesPerUint32;
        }
        if (p == 3 || p == 5 || p == 6) {
            uint32Count++;
        }
        return uint32Count;
    }

    public int translateBlockRuntimeId(int input, int output, int blockRuntimeId) {
        val inputDict = BlockStateDictionary.getInstance(input);

        val stateHash = inputDict.toStateHash(blockRuntimeId);

        int fallback = BlockStateDictionary.getInstance(output).getFallback();
        if (stateHash == null) {
            log.error("unknown block runtime id {}", blockRuntimeId);
            return blockRuntimeId;
        }

        var translated = BlockStateDictionary.getInstance(output).toRuntimeId(stateHash);
        if (translated == null) {
            /*
            //TODO HACK for heads and skulls, this is not a proper way to translate them. currently unusable
            var anyState = inputDict.lookupStateFromStateHash(stateHash);
            if (anyState != null && (anyState.name().endsWith("_head") || anyState.name().endsWith("_skull"))) {
                Integer skullHash = inputDict.lookupStateIdFromData("minecraft:skeleton_skull", anyState.stateData());
                if (skullHash != null) {
                    return Objects.requireNonNullElse(BlockStateDictionary.getInstance(output).toRuntimeId(skullHash), fallback);
                }
            }
             */
            return fallback;
        }
        return translated;
    }

    public BlockDefinition translateBlockDefinition(int input, int output, BlockDefinition definition) {
        int fallback = BlockStateDictionary.getInstance(output).getFallback();
        val stateHash = BlockStateDictionary.getInstance(input).toStateHash(definition.getRuntimeId());
        if (stateHash == null) {
            log.error("1 translateBlockDefinition: protocol: {}->{}, id={}->{}", input, output, definition.getRuntimeId(), fallback);
            return () -> fallback;
        }

        val oldState = BlockStateDictionary.getInstance(input).lookupStateFromStateHash(stateHash);
        val converted = BlockStateDictionary.getInstance(output).toRuntimeId(oldState.stateHash());
        if (converted == null) {
            log.error("2 translateBlockDefinition: protocol: {}->{}, name=>{}->{} id={}->{}", input, output, oldState, "minecraft:info_update", definition.getRuntimeId(), fallback);
            return () -> fallback;
        }
        val newState = BlockStateDictionary.getInstance(output).lookupStateFromStateHash(stateHash);
        log.debug("3 translateBlockDefinition: protocol: {}->{}, name=>{}->{} id={}->{}", input, output, oldState, newState, definition.getRuntimeId(), converted);
        return () -> converted;
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
}
