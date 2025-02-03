package com.github.blackjack200.ouranos.network.convert.palette;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author JukeboxMC | daoge_cmd | CoolLoong | Blackjack200
 */
@Slf4j
public final class Palette<V> {
    private final List<V> palette = new ArrayList<>();
    private int blockSize;
    private ByteBuf bitArray;

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

    public void writeNetwork(ByteBuf out, Function<V, Integer> serializer) {
        if (this.blockSize == 0) {
            out.writeByte(1);
            VarInts.writeInt(out, serializer.apply(palette.get(0)));
            return;
        }
        out.writeByte((blockSize << 1) | 1);
        if (this.blockSize == 0x7F) {
            return;
        }

        if (this.bitArray != null) {
            out.writeBytes(this.bitArray);
        }

        VarInts.writeInt(out, this.palette.size());

        this.palette.forEach(value -> VarInts.writeInt(out, serializer.apply(value)));
    }

    public void readNetwork(ByteBuf in, Function<Integer, V> deserializer) throws PaletteException {
        var header = in.readUnsignedByte();
        if ((header & 1) == 0) {
            throw new PaletteException("Reading persistent data with runtime method!");
        }
        blockSize = header >> 1;
        if (blockSize == 0x7F) {
            return;
        }

        this.palette.clear();
        var paletteCount = 1;

        if (blockSize == 0) {
            this.bitArray = null;
            this.palette.add(deserializer.apply(VarInts.readInt(in)));
            return;
        }

        this.bitArray = in.readBytes(uint32s(blockSize) * 4);

        paletteCount = VarInts.readInt(in);

        for (int i = 0; i < paletteCount; i++) {
            this.palette.add(deserializer.apply(VarInts.readInt(in)));
        }
    }
}