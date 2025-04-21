package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import io.netty.buffer.*;
import io.netty.util.ReferenceCountUtil;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;

import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@UtilityClass
public class TypeConverter {
    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull() || !itemData.isValid()) {
            return itemData;
        }

        //downgrade item type
        var def = itemData.getDefinition();

        var state = BlockStateDictionary.getInstance(input).lookupStateIdFromIdMeta(def.getIdentifier(), itemData.getDamage());

        Object[] translatedIdMeta = new Object[]{def.getIdentifier(), itemData.getDamage()};

        if (state != null) {
            var cur = BlockStateDictionary.getInstance(output);
            var oldState = cur.lookupStateFromStateHash(state.latestStateHash());
            if (oldState != null) {
                translatedIdMeta = new Object[]{oldState.name(), oldState.meta()};
            }
        }

        translatedIdMeta = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(translatedIdMeta[0].toString(), (Integer) translatedIdMeta[1]);
        translatedIdMeta = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(translatedIdMeta[0].toString(), (Integer) translatedIdMeta[1]);

        String newStringId = translatedIdMeta[0].toString();
        var newMeta = (Integer) translatedIdMeta[1];
        //log.info("old_id={}:{} new_id={}:{}", itemData.getDefinition().getIdentifier(), itemData.getDamage(), newStringId, newMeta);

        var itemDict = ItemTypeDictionary.getInstance(output);
        var itemTypeInfo = itemDict.getEntries().getOrDefault(newStringId, null);
        if (itemTypeInfo == null) {
            //log.error("Unknown glk type {}", newStringId);
            return null;
        }

        var builder = itemData.toBuilder();
        builder.definition(itemTypeInfo.toDefinition(newStringId))
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
    public int rewriteFullChunk(int input, int output, ByteBuf from, ByteBuf to, int dimension, int sections) throws ChunkRewriteException {
        for (var section = 0; section < sections; section++) {
            if (output < Bedrock_v475.CODEC.getProtocolVersion() && section == 4) {
                to.clear();
            }
            rewriteSubChunk(input, output, from, to);
        }

        var biomeBuf = rewriteBiomePalette(input, output, from, dimension);
        to.writeBytes(biomeBuf);
        ReferenceCountUtil.release(biomeBuf);

        var borderBlocks = from.readByte();
        to.writeByte(borderBlocks);
        to.writeBytes(from, borderBlocks);

        if (!Ouranos.getOuranos().getConfig().crop_chunk_tile) {
            rewriteBlockEntities(input, output, from, to);
        }
        if (output < Bedrock_v475.CODEC.getProtocolVersion()) {
            return sections - 4;
        }
        return sections;
    }

    private static ByteBuf rewriteBiomePalette(int input, int output, ByteBuf from, int dimension) throws ChunkRewriteException {
        var biomeBuf = ByteBufAllocator.DEFAULT.buffer().touch();
        if (input >= Bedrock_v475.CODEC.getProtocolVersion()) {
            var single = AbstractByteBufAllocator.DEFAULT.heapBuffer();
            var firstTime = true;
            var biomeId = new AtomicReference<>((byte) 0);
            for (int x = getDimensionChunkBounds(input, dimension); x > 0; x--) {
                PaletteStorage.translatePaletteStorage(input, output, from, biomeBuf, (i, o, v) -> {
                    biomeId.set((byte) (v & 0xFF));
                    return v;
                });
                if (firstTime) {
                    single.writeBytes(biomeBuf);
                    firstTime = false;
                }
            }
            biomeBuf.clear();
            if (output > Bedrock_v475.CODEC.getProtocolVersion()) {
                for (var i = 0; i < getDimensionChunkBounds(output, dimension); i++) {
                    biomeBuf.writeBytes(single);
                }
            } else {
                var newBiomes2D = new byte[256];
                for (int xx = 0; xx < 16; xx++) {
                    for (int zz = 0; zz < 16; zz++) {
                        newBiomes2D[(xx & 15) | (zz & 15) << 4] = biomeId.get();
                    }
                }
                biomeBuf.writeBytes(newBiomes2D);
            }
            ReferenceCountUtil.release(single);
        } else {
            from.readBytes(256);
        }
        return biomeBuf;
    }

    @SneakyThrows
    public static void rewriteBlockEntities(int input, int output, ByteBuf from, ByteBuf to) {
        var inp = new ByteBufInputStream(from);
        var reader = NbtUtils.createNetworkReader(inp);
        var rd = NbtUtils.createNetworkWriter(new ByteBufOutputStream(to));
        while (inp.available() > 0) {
            var tag = (NbtMap) reader.readTag();
            var id = tag.getString("id");
            if (id.isEmpty()) {
                continue;
            }
            log.info(tag);
            rd.writeTag(tag);
        }
    }

    private int getDimensionChunkBounds(int protocol, int dimension) {
        switch (dimension) {
            case 0://overworld
                return protocol >= Bedrock_v503.CODEC.getProtocolVersion() ? 24 : 25;
            case 1://nether
                return 8;
            case 2://the_end
                return 16;
            default:
                log.debug("Unknown dimension for chunk bounds: {}", dimension);
                return protocol >= Bedrock_v503.CODEC.getProtocolVersion() ? 24 : 25;
        }
    }

    public static void rewriteSubChunk(int input, int output, ByteBuf from, ByteBuf to) throws ChunkRewriteException {
        var version = from.readUnsignedByte();
        to.writeByte(version);
        switch (version) {
            case 0, 4, 139 -> to.writeBytes(from, 4096 + 2048);
            case 1 ->
                    PaletteStorage.translatePaletteStorage(input, output, from, to, TypeConverter::translateBlockRuntimeId);
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
            default -> // Unsupported
                    throw new ChunkRewriteException("ChunkDataRewrite: Unknown subchunk format " + version);
        }
    }

    public int translateBlockRuntimeId(int input, int output, int blockRuntimeId) {
        val inputDict = BlockStateDictionary.getInstance(input);
        val outputDict = BlockStateDictionary.getInstance(output);

        val stateHash = inputDict.toLatestStateHash(blockRuntimeId);

        if (stateHash == null) {
            return outputDict.getFallbackRuntimeId();
        }

        var translated = outputDict.toRuntimeId(stateHash);
        if (translated == null) {
            var anyState = inputDict.lookupStateFromStateHash(stateHash);
            if (anyState != null) {
                Object[] translatedIdMeta = new Object[]{anyState.name(), anyState.meta()};
                translatedIdMeta = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(translatedIdMeta[0].toString(), (Integer) translatedIdMeta[1]);
                var skullHash = outputDict.lookupStateIdFromIdMeta(translatedIdMeta[0].toString(), (Integer) translatedIdMeta[1]);
                if (skullHash != null) {
                    Integer rtId = outputDict.toRuntimeId(skullHash.latestStateHash());
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
            return new DefaultDescriptor(typ.toDefinition(newStringId), newMeta);
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
