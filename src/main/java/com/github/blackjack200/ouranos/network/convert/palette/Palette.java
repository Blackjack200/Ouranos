package com.github.blackjack200.ouranos.network.convert.palette;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.List;

/**
 * @author JukeboxMC | daoge_cmd | CoolLoong
 */
@Slf4j
public final class Palette<V> {
    private final List<V> palette = new ArrayList<>();
    private BitArrayVersion version = BitArrayVersion.V0;
    private ByteBuf bitArray;
    private boolean equalLast = false;

    public void writeNetwork(ByteBuf out, RuntimeDataSerializer<V> serializer) {
        if (this.version == BitArrayVersion.V0) {
            out.writeByte((BitArrayVersion.V0.bits << 1) | 1);
            VarInts.writeInt(out, serializer.serialize(palette.get(0)));
            return;
        }

        byte blockSize = this.version.bits;
        if (this.equalLast) {
            blockSize = 0x7F;
        }
        out.writeByte((blockSize << 1) | 1);
        if (this.equalLast) {
            return;
        }

        if (this.bitArray != null) {
            out.writeBytes(this.bitArray);
        }

        VarInts.writeInt(out, this.palette.size());

        this.palette.forEach(value -> VarInts.writeInt(out, serializer.serialize(value)));
    }

    public void readNetwork(ByteBuf in, RuntimeDataDeserializer<V> deserializer) throws PaletteException {
        var header = in.readUnsignedByte();
        val blockSize = header >> 1;
        if ((header & 1) == 0) {
            throw new PaletteException("Reading persistent data with runtime method!");
        }
        if (blockSize == 0x7F) {
            this.equalLast = true;
            return;
        }

        this.version = BitArrayVersion.get(blockSize, true);

        if (version == null) {
            throw new PaletteException("Unknown bit array version: " + (blockSize));
        }

        this.palette.clear();
        var paletteCount = 1;

        if (version == BitArrayVersion.V0) {
            this.bitArray = null;
            this.palette.add(deserializer.deserialize(VarInts.readInt(in)));
            return;
        }

        var wordCount = version.getWordsForSize(16 * 16 * 16);
        this.bitArray = in.readBytes(wordCount * 4);

        paletteCount = VarInts.readInt(in);
        if (version.maxEntryIndex < paletteCount - 1) {
            throw new PaletteException("Palette (version " + version.name() + ") is too large. Max size " + version.maxEntryIndex + ". Actual size " + paletteCount);
        }
        for (int i = 0; i < paletteCount; i++) {
            this.palette.add(deserializer.deserialize(VarInts.readInt(in)));
        }
    }
}