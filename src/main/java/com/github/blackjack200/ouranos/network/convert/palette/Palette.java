package com.github.blackjack200.ouranos.network.convert.palette;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.val;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author JukeboxMC | daoge_cmd | CoolLoong | Blackjack200
 */
public final class Palette<V> implements AutoCloseable {
    private final int blockSize;
    private final List<V> palette;
    private final ByteBuf bitArray;

    private Palette(int blockSize, List<V> palette, ByteBuf bitArray) {
        this.blockSize = blockSize;
        this.palette = palette;
        this.bitArray = bitArray;
    }

    @Override
    public void close() throws Exception {
        ReferenceCountUtil.safeRelease(this.bitArray);
    }

    private static int uint32s(int p) {
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

    public static <V> Palette<V> readNetwork(ByteBuf in, Function<Integer, V> deserializer) throws PaletteException {
        var header = in.readUnsignedByte();
        if ((header & 1) == 0) {
            throw new PaletteException("Reading persistent data with runtime method!");
        }

        int blockSize = header >> 1;
        if (blockSize == 0) {
            return new Palette<>(blockSize, List.of(deserializer.apply(VarInts.readInt(in))), null);
        }
        if (blockSize == 0x7F) {
            return new Palette<>(blockSize, Collections.emptyList(), null);
        }

        ByteBuf bitArray = in.readBytes(uint32s(blockSize) * 4);

        var paletteCount = VarInts.readInt(in);

        if (paletteCount == -1) {
            return new Palette<>(0x7F, Collections.emptyList(), null);
        }

        var palette = new ArrayList<V>(paletteCount);

        for (int i = 0; i < paletteCount; i++) {
            palette.add(deserializer.apply(VarInts.readInt(in)));
        }
        return new Palette<>(blockSize, palette, bitArray);
    }

}