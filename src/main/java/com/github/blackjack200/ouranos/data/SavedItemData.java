package com.github.blackjack200.ouranos.data;

import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import org.cloudburstmc.nbt.NbtMap;

public record SavedItemData(String name, int meta, BlockStateDictionary.Dictionary.BlockEntry block, NbtMap tag) {
}
