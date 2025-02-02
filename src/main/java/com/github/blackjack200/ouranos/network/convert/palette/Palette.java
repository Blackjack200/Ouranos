package com.github.blackjack200.ouranos.network.convert.palette;

import com.github.blackjack200.ouranos.network.convert.bitarray.BitArray;
import com.github.blackjack200.ouranos.network.convert.bitarray.BitArrayVersion;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author JukeboxMC | daoge_cmd | CoolLoong
 */
@Slf4j
public final class Palette<V> {

    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final BitArrayVersion INITIAL_VERSION = BitArrayVersion.V0;

    private List<V> palette;
    private BitArray bitArray;

    public Palette(V first) {
        this(first, INITIAL_VERSION);
    }

    public Palette(V first, BitArrayVersion version) {
        this(first, new ArrayList<>(version.maxEntryIndex + 1), version);
    }

    public Palette(V first, List<V> palette, BitArrayVersion version) {
        this.bitArray = version.createArray(SECTION_SIZE);
        this.palette = palette;
        // Please note that the first entry shouldn't be changed
        this.palette.add(first);
    }

    private static boolean isPersistent(short header) {
        return (header & 1) == 0;
    }

    public void writeToNetwork(ByteBuf byteBuf, RuntimeDataSerializer<V> serializer) {
        if (bitArray.version() == BitArrayVersion.V0) {
            byteBuf.writeByte(getPaletteHeader(BitArrayVersion.V0, true));
            VarInts.writeInt(byteBuf, serializer.serialize(palette.get(0)));
            return;
        }

        byteBuf.writeByte(getPaletteHeader(this.bitArray.version(), true));

        for (int word : this.bitArray.words()) {
            byteBuf.writeIntLE(word);
        }
        VarInts.writeInt(byteBuf, this.palette.size());

        this.palette.forEach(value -> VarInts.writeInt(byteBuf, serializer.serialize(value)));
    }

    public void readFromStorageRuntime(ByteBuf byteBuf, RuntimeDataDeserializer<V> deserializer, Palette<V> last) {
        var header = byteBuf.readUnsignedByte();
        if (isPersistent(header)) {
            log.warn("Reading persistent data with runtime method!");
        }
        if (hasCopyLastFlag(header)) {
            last.copyTo(this);
            return;
        }

        var version = getVersionFromPaletteHeader(header);
        this.palette.clear();
        var paletteSize = 1;

        if (version == BitArrayVersion.V0) {
            this.bitArray = version.createArray(SECTION_SIZE, null);
            this.palette.add(deserializer.deserialize(VarInts.readInt(byteBuf)));
            return;
        }

        readWords(byteBuf, version);
        paletteSize = VarInts.readInt(byteBuf);
        checkVersion(version, paletteSize);

        for (int i = 0; i < paletteSize; i++) {
            this.palette.add(deserializer.deserialize(VarInts.readInt(byteBuf)));
        }
    }

    public void copyTo(Palette<V> palette) {
        palette.bitArray = this.bitArray.copy();
        palette.palette.clear();
        palette.palette.addAll(this.palette);
    }

    public BitArrayVersion getVersion() {
        return bitArray.version();
    }

    private void readWords(ByteBuf byteBuf, BitArrayVersion version) {
        var wordCount = version.getWordsForSize(SECTION_SIZE);
        var words = new int[wordCount];
        Arrays.setAll(words, i -> byteBuf.readIntLE());

        this.bitArray = version.createArray(SECTION_SIZE, words);
    }

    private static boolean hasCopyLastFlag(short header) {
        return (header >> 1) == 0x7F;
    }

    private static short getPaletteHeader(BitArrayVersion version, boolean runtime) {
        return (short) ((version.bits << 1) | (runtime ? 1 : 0));
    }

    private static BitArrayVersion getVersionFromPaletteHeader(short header) {
        return BitArrayVersion.get(header >> 1, true);
    }

    private static void checkVersion(BitArrayVersion version, int paletteSize) {
        if (version.maxEntryIndex < paletteSize - 1) {
            throw new PaletteException("Palette (version " + version.name() + ") is too large. Max size " + version.maxEntryIndex + ". Actual size " + paletteSize);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Palette<?> palette1)) return false;
        return Objects.equals(palette, palette1.palette) && Objects.equals(bitArray, palette1.bitArray);
    }
}