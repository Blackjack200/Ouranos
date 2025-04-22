package com.github.blackjack200.ouranos.network.convert.bitarray;

/**
 * @author JukeboxMC | daoge_cmd
 */
public interface BitArray {

    void set(int index, int value);

    int get(int index);

    int size();

    int[] words();

    BitArrayVersion version();

    BitArray copy();
}