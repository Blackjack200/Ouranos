package com.github.blackjack200.ouranos.network.convert.palette;

import com.github.blackjack200.ouranos.network.convert.bitarray.BitArray;
import com.github.blackjack200.ouranos.network.convert.bitarray.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author JukeboxMC | daoge_cmd | CoolLoong | Blackjack200
 */
public final class Palette<V> {
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private final int blockSize;
    @Getter
    private final List<V> palette;
    private BitArray bitArray;

    private Palette(int blockSize, List<V> palette, BitArray bitArray) {
        this.blockSize = blockSize;
        this.palette = palette;
        this.bitArray = bitArray;
    }

    public Palette(V first) {
        this.blockSize = BitArrayVersion.V2.bits;
        this.bitArray = BitArrayVersion.V2.createArray(SECTION_SIZE);
        this.palette = new ArrayList<>(16);
        this.palette.add(first);
    }

    public V get(int index) {
        return this.palette.get(this.bitArray.get(index));
    }

    private int paletteIndexFor(V value) throws PaletteException {
        var index = this.palette.indexOf(value);
        if (index != -1) {
            return index;
        }

        index = this.palette.size();
        this.palette.add(value);

        var version = this.bitArray.version();
        if (index > version.maxEntryIndex) {
            var next = version.next;
            if (next != null) {
                this.onResize(next);
            } else {
                throw new PaletteException("Palette have reached the max bit array version");
            }
        }

        return index;
    }

    private void onResize(BitArrayVersion version) {
        var newBitArray = version.createArray(SECTION_SIZE);
        for (int i = 0; i < SECTION_SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }

        this.bitArray = newBitArray;
    }

    public void set(int index, V value) throws PaletteException {
        var paletteIndex = this.paletteIndexFor(value);
        this.bitArray.set(index, paletteIndex);
    }

    public void set(int x, int y, int z, V value) throws PaletteException {
        this.set(index(x, y, z), value);
    }

    public V get(int x, int y, int z) throws PaletteException {
        return this.get(index(x, y, z));
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

    public void writeNetwork(ByteBuf out, int protocol, Function<V, Integer> serializer) {
        if (this.blockSize == 0) {
            if (protocol >= Bedrock_v465.CODEC.getProtocolVersion()) {
                out.writeByte(1);
            } else {
                out.writeByte((1 << 1) | 1);
                out.writeBytes(new byte[512]);
                VarInts.writeInt(out, 1);
            }
            VarInts.writeInt(out, serializer.apply(palette.get(0)));
            return;
        }
        out.writeByte((blockSize << 1) | 1);
        if (this.blockSize == 0x7F) {
            return;
        }

        if (this.bitArray != null) {
            for (int word : this.bitArray.words()) {
                out.writeIntLE(word);
            }
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
            return new Palette<>(blockSize, List.of(deserializer.apply(VarInts.readInt(in))), BitArrayVersion.V0.createArray(SECTION_SIZE, null));
        }
        if (blockSize == 0x7F) {
            return new Palette<>(blockSize, Collections.emptyList(), null);
        }


        var wordCount = uint32s(blockSize);
        var words = new int[wordCount];
        Arrays.setAll(words, i -> in.readIntLE());

        var bitArray = BitArrayVersion.get(blockSize, true).createArray(SECTION_SIZE, words);

        var paletteCount = VarInts.readInt(in);

        if (paletteCount == -1) {
            return new Palette<>(0x7F, Collections.emptyList(), BitArrayVersion.V0.createArray(SECTION_SIZE, null));
        }

        var palette = new ArrayList<V>(paletteCount);

        for (int i = 0; i < paletteCount; i++) {
            palette.add(deserializer.apply(VarInts.readInt(in)));
        }
        return new Palette<>(blockSize, palette, bitArray);
    }

    static int index(int x, int y, int z) {
        return (x << 8) + (z << 4) + y;
    }
}