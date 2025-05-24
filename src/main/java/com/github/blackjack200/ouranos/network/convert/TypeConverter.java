package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.network.convert.palette.Palette;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import com.github.blackjack200.ouranos.utils.SimpleVersionedItemDefinition;
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
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;

import java.util.ArrayList;

@Log4j2
@UtilityClass
public class TypeConverter {
    public int[] translateItemRuntimeId(int input, int output, int runtimeId, int meta) {
        var newItem = TypeConverter.translateItemData(input, output, ItemData.builder().definition(new SimpleItemDefinition("", runtimeId, false)).damage(meta).count(1).build());
        return new int[]{newItem.getDefinition().getRuntimeId(), newItem.getDamage()};
    }

    public ItemData translateItemData(int input, int output, ItemData itemData) {
        if (itemData.isNull() || !itemData.isValid()) {
            return itemData;
        }

        if (itemData.getTag() != null) {
            var polyfill = ItemTranslator.recoverPolyfillItem(itemData);
            if (polyfill != null) {
                return polyfill;
            }
        }

        var def = itemData.getDefinition();
        var builder = itemData.toBuilder();

        var translatedId = def.getIdentifier();
        if (translatedId.isEmpty()) {
            translatedId = ItemTypeDictionary.getInstance(input).fromIntId(def.getRuntimeId());
        }
        var translatedMeta = itemData.getDamage();

        var rawData = GlobalItemDataHandlers.getUpgrader().idMetaUpgrader().upgrade(translatedId, translatedMeta);
        translatedId = rawData[0].toString();
        translatedMeta = (Integer) rawData[1];

        rawData = GlobalItemDataHandlers.getItemIdMetaDowngrader(output).downgrade(translatedId, translatedMeta);
        translatedId = rawData[0].toString();
        translatedMeta = (Integer) rawData[1];

        if (itemData.getBlockDefinition() != null) {
            var inputDict = BlockStateDictionary.getInstance(input);
            var outputDict = BlockStateDictionary.getInstance(output);

            var inputState = inputDict.lookupStateFromStateHash(inputDict.toLatestStateHash(itemData.getBlockDefinition().getRuntimeId()));

            var outputState = outputDict.lookupStateFromStateHash(inputState.latestStateHash());
            if (outputState != null) {
                builder.blockDefinition(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition(outputState.name(), outputDict.toRuntimeId(outputState.latestStateHash()), outputState.rawState()));
            } else {
                builder.blockDefinition(null);
            }
        }

        var itemDict = ItemTypeDictionary.getInstance(output);
        var itemTypeInfo = itemDict.getEntries().getOrDefault(translatedId, null);
        if (itemTypeInfo == null) {
            return ItemTranslator.makePolyfillItem(input, output, itemData);
        }
        builder.definition(itemTypeInfo.toDefinition(translatedId))
                .damage(translatedMeta);
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
            int aux = itemData.getDamage();
            log.info("aux {} => {}", d.getAuxValue(), aux);
            return new DefaultDescriptor(itemData.getDefinition(), aux);
        } else if (descriptor instanceof ItemTagDescriptor d) {
            if (output < Bedrock_v554.CODEC.getProtocolVersion()) {
                Integer runtimeId = ItemTypeDictionary.getInstance(output).fromStringId(d.getItemTag());
                if (runtimeId == null) {
                    return InvalidDescriptor.INSTANCE;
                }
                var def = new SimpleVersionedItemDefinition(d.getItemTag(), runtimeId, ItemVersion.LEGACY, false, NbtMap.EMPTY);
                return new DefaultDescriptor(def, -1);
            }
            return d;
        }
        throw new RuntimeException("unknown descriptor " + descriptor);
    }

    public static CreativeItemData translateCreativeItemData(int input, int output, CreativeItemData itemData) {
        var item = translateItemData(input, output, itemData.getItem());
        return new CreativeItemData(item, itemData.getNetId(), itemData.getGroupId());
    }
}
