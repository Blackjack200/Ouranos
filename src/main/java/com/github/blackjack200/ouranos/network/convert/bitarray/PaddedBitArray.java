package com.github.blackjack200.ouranos.network.convert.bitarray;

import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.common.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author JukeboxMC | daoge_cmd
 */
public record PaddedBitArray(BitArrayVersion version, int size, int[] words) implements BitArray {

    public PaddedBitArray(BitArrayVersion version, int size, int[] words) {
        this.size = size;
        this.version = version;
        this.words = words;

        var expectedWordsLength = GenericMath.ceil((float) size / version.entriesPerWord);
        if (words.length != expectedWordsLength) {
            throw new IllegalArgumentException("Invalid length given for storage, got: " + words.length +
                                               " but expected: " + expectedWordsLength);
        }
    }

    @Override
    public void set(int index, int value) {
        Preconditions.checkElementIndex(index, this.size);
        if (value < 0 || value > this.version.maxEntryIndex) {
            throw new IllegalArgumentException(String.format("Max value: %s. Received value %s", this.version.maxEntryIndex, value));
        }

        var arrayIndex = index / this.version.entriesPerWord;
        var offset = (index % this.version.entriesPerWord) * this.version.bits;
        this.words[arrayIndex] = this.words[arrayIndex] & ~(this.version.maxEntryIndex << offset) | (value & this.version.maxEntryIndex) << offset;
    }

    @Override
    public int get(int index) {
        Preconditions.checkElementIndex(index, this.size);
        var arrayIndex = index / this.version.entriesPerWord;
        var offset = (index % this.version.entriesPerWord) * this.version.bits;
        return (this.words[arrayIndex] >>> offset) & this.version.maxEntryIndex;
    }

    @Override
    public BitArray copy() {
        return new PaddedBitArray(this.version, this.size, Arrays.copyOf(this.words, this.words.length));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaddedBitArray that)) return false;
        return size == that.size && version == that.version && Arrays.equals(words, that.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, size, Arrays.hashCode(words));
    }
}
