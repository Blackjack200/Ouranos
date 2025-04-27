package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.network.convert.palette.Palette;
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
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;

import java.util.ArrayList;

@Log4j2
@UtilityClass
public class TypeConverter {

    public static final String POLYFILL_ITEM_TAG = "____Ouranos____";

    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull() || !itemData.isValid()) {
            return itemData;
        }
        if (itemData.getTag() != null) {
            var polyfillData = itemData.getTag().getCompound(POLYFILL_ITEM_TAG);
            if (polyfillData != NbtMap.EMPTY) {
                var itemDict = ItemTypeDictionary.getInstance(polyfillData.getInt("Source"));
                var builder = ItemData.builder()
                        .definition(itemDict.getEntries().get(itemDict.fromIntId(polyfillData.getInt("ItemId"))).toDefinition(polyfillData.getString("StringId")))
                        .tag(polyfillData.getCompound("Nbt"))
                        .count(itemData.getCount())
                        .damage(itemData.getDamage())
                        .netId(itemData.getNetId())
                        .canBreak(itemData.getCanBreak())
                        .canPlace(itemData.getCanPlace())
                        .blockingTicks(itemData.getBlockingTicks())
                        .usingNetId(itemData.isUsingNetId());
                if (polyfillData.containsKey("BlockId")) {
                    builder.blockDefinition(new SimpleBlockDefinition(polyfillData.getInt("BlockId")));
                }
                return builder.build();
            }
        }

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
            var polyfillItem = ItemData.builder().netId(itemData.getNetId()).count(itemData.getCount()).damage(itemData.getDamage()).definition(ItemTypeDictionary.getInstance(output).getEntries().get("minecraft:barrier").toDefinition("minecraft:barrier"));
            var polyfillData = NbtMap.builder()
                    .putInt("Source", input)
                    .putString("StringId", def.getIdentifier())
                    .putInt("ItemId", def.getRuntimeId());
            if (itemData.getBlockDefinition() != null) {
                polyfillData.putInt("BlockId", itemData.getBlockDefinition().getRuntimeId());
            }
            if (itemData.getTag() != null) {
                polyfillData.putCompound("Nbt", itemData.getTag());
            }
            polyfillItem.tag(NbtMap.builder()
                    .putCompound("display", NbtMap.builder().putString("Name", def.getIdentifier()).build())
                    .putCompound(POLYFILL_ITEM_TAG, polyfillData.build())
                    .build()
            );
            return polyfillItem.build();
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
        var subChunks = new ArrayList<ByteBuf>();
        for (var section = 0; section < sections; section++) {
            var buf = ByteBufAllocator.DEFAULT.buffer();
            rewriteSubChunk(input, output, from, buf);
            subChunks.add(buf);
        }
        var allSubChunks = new ArrayList<>(subChunks);
        if (subChunks.size() >= 4 && input > Bedrock_v475.CODEC.getProtocolVersion() && output < Bedrock_v475.CODEC.getProtocolVersion()) {
            subChunks.subList(0, 4).clear();
        }
        if (subChunks.size() >= 20 && input > Bedrock_v465.CODEC.getProtocolVersion() && output < Bedrock_v465.CODEC.getProtocolVersion()) {
            subChunks.subList(subChunks.size() - 4, subChunks.size()).clear();
        }
        for (var subChunk : subChunks) {
            to.writeBytes(subChunk);
        }
        for (var subChunk : allSubChunks) {
            ReferenceCountUtil.release(subChunk);
        }

        if (output < Bedrock_v361.CODEC.getProtocolVersion()) {
            to.writeBytes(new byte[512]);
        }
        var biomeBuf = rewriteBiomePalette(input, output, from, getDimensionChunkBounds(input, dimension), getDimensionChunkBounds(output, dimension));
        to.writeBytes(biomeBuf);
        ReferenceCountUtil.release(biomeBuf);

        var borderBlocks = from.readByte();
        to.writeByte(borderBlocks);
        to.writeBytes(from, borderBlocks);

        rewriteBlockEntities(input, output, from, to);
        return subChunks.size();
    }

    private static ByteBuf rewriteBiomePalette(int input, int output, ByteBuf from, int nInputSection, int nOutputSection) throws ChunkRewriteException {
        var biomeBuf = ByteBufAllocator.DEFAULT.buffer().touch();
        var palettes = new ArrayList<Palette<Integer>>();
        if (input >= Bedrock_v475.CODEC.getProtocolVersion()) {
            for (int x = nInputSection; x > 0; x--) {
                palettes.add(Palette.readNetwork(from, (v) -> v));
            }
        } else {
            var biomeData = from.readBytes(256);
            var palette = new Palette<>(0);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int biomeId = biomeData.getByte((z << 4) | x);
                    for (int y = 0; y < 16; y++) {
                        palette.set(x, z, y, biomeId);
                    }
                }
            }
            for (int x = nInputSection; x > 0; x--) {
                palettes.add(palette);
            }
        }
        if (output >= Bedrock_v475.CODEC.getProtocolVersion()) {
            if (nInputSection > nOutputSection) {
                palettes = new ArrayList<>(palettes.subList(0, nOutputSection));
            } else if (nInputSection < nOutputSection) {
                var firstPalette = palettes.isEmpty() ? new Palette<Integer>(0) : palettes.get(palettes.size() - 1);
                for (int i = 0; i < nOutputSection - nInputSection; i++) {
                    palettes.add(firstPalette);
                }
            }

            for (var palette : palettes) {
                palette.writeNetwork(biomeBuf, output, (v) -> v);
            }
        } else {
            var palette = palettes.get(0);
            var bytes = new byte[256];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int biomeId = palette.get(x, 0, z);
                    for (int y = 0; y < 16; y++) {
                        bytes[(z << 4) | x] = (byte) (biomeId & 0xff);
                    }
                }
            }
            biomeBuf.writeBytes(bytes);
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
            rd.writeTag(translateBlockEntity(input, output, tag));
        }
    }

    private static NbtMap translateBlockEntity(int input, int output, NbtMap tag) {
        var builder = tag.toBuilder();
        var id = tag.getString("id");
        if (id.equals("Sign")) {
            if (tag.containsKey("FrontText")) {
                builder.putString("Text", tag.getCompound("FrontText").getString("Text"));
            }
        }
        return builder.build();
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
        var isNineSubChunkSupported = output >= Bedrock_v465.CODEC.getProtocolVersion();
        if (!isNineSubChunkSupported && version == 9) {
            to.writeByte(8);
        } else {
            to.writeByte(version);
        }
        switch (version) {
            case 0, 4, 139 -> to.writeBytes(from, 4096 + 2048);
            case 1 ->
                    PaletteStorage.translatePaletteStorage(input, output, from, to, TypeConverter::translateBlockRuntimeId);
            case 8, 9 -> { // New form chunk, baked-in palette
                var storageCount = from.readUnsignedByte();
                to.writeByte(storageCount);
                if (version == 9) {
                    var v = from.readUnsignedByte();//what ??? uint8(index + (c.range[0] >> 4))
                    if (isNineSubChunkSupported) {
                        to.writeByte(v);
                    }
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
            var entry = inputDict.lookupStateFromStateHash(stateHash);
            if (entry.name().contains("_head") || entry.name().contains("_skull")) {
                entry = inputDict.lookupStateIdFromIdMeta("minecraft:skeleton_skull", entry.meta());
            }
            if (entry != null) {
                translated = outputDict.toRuntimeId(entry.latestStateHash());
            }
            if (translated != null) {
                return translated;
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
        return new CreativeItemData(item, itemData.getNetId(), itemData.getGroupId());
    }
}
