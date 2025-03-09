package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.util.ReferenceCountUtil;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;

import java.io.IOException;

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

        var itemDict = ItemTypeDictionary.getInstance(output);
        var itemTypeInfo = itemDict.getEntries().getOrDefault(newStringId, null);
        if (itemTypeInfo == null) {
            //log.error("Unknown glk type {}", newStringId);
            return null;
        }

        var builder = itemData.toBuilder();
        builder.definition(new SimpleItemDefinition(newStringId, itemTypeInfo.runtime_id(), itemTypeInfo.component_based()))
                .damage(newMeta);

        if (itemData.getBlockDefinition() != null) {
            if (itemData.getBlockDefinition().getRuntimeId() > 0) {
                var outputDict = BlockStateDictionary.getInstance(output);
                var blkInfo = BlockStateDictionary.getInstance(input).toBlockState(itemData.getBlockDefinition().getRuntimeId());
                if (blkInfo != null) {
                    var x = outputDict.lookupStateFromStateHash(blkInfo.latestStateHash());
                    if (x != null) {
                        builder.blockDefinition(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition(x.name(), outputDict.toRuntimeId(x.latestStateHash()), x.rawState()));
                    }
                } else {
                    builder.blockDefinition(new SimpleBlockDefinition(outputDict.getFallbackRuntimeId()));
                }
            }
        }
        return builder.build();
    }

    @SneakyThrows
    public void rewriteFullChunk(int input, int output, ByteBuf from, ByteBuf to, int dimension, int sections) throws ChunkRewriteException {
        for (var section = 0; section < sections; section++) {
            if (output < Bedrock_v475.CODEC.getProtocolVersion() && section == 4) {
                to.clear();
            }
            if (rewriteSubChunk(input, output, from, to)) {
                return;
            }
        }

        var buf = ByteBufAllocator.DEFAULT.buffer().touch();
        for (int x = getDimensionChunkBounds(input, dimension); x > 0; x--) {
            PaletteStorage.translatePaletteStorage(input, output, from, buf, (i, o, v) -> v);
        }
        if (output < Bedrock_v475.CODEC.getProtocolVersion()) {
            //TODO implement biome & block entities rewrite
        } else {
            //TODO wtf this may crashes the client
            //to.writeBytes(buf);
            to.writeByte(from.readByte());
            rewriteBlockEntities(input, output, from, to);
        }
        ReferenceCountUtil.release(buf);
    }

    private static void rewriteBlockEntities(int input, int output, ByteBuf from, ByteBuf to) throws IOException {
        var inp = new ByteBufInputStream(from);
        var reader = NbtUtils.createNetworkReader(inp);
        var rd = NbtUtils.createNetworkWriter(new ByteBufOutputStream(to));
        while (inp.available() > 0) {
            rd.writeTag(reader.readTag());
        }
    }

    private int getDimensionChunkBounds(int protocol, int dimension) {
        switch (dimension) {
            case 0://overworld
                return protocol <= Bedrock_v503.CODEC.getProtocolVersion() ? 25 : 24;
            case 1://nether
                return 8;
            case 2://the_end
                return 16;
            default:
                log.debug("Unknown dimension for chunk bounds: {}", dimension);
                return protocol <= Bedrock_v503.CODEC.getProtocolVersion() ? 25 : 24;
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
