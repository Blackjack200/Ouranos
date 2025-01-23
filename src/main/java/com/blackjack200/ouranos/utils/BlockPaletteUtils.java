package com.blackjack200.ouranos.utils;

import lombok.Value;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.TreeMap;

public class BlockPaletteUtils {
    public static int createHash(NbtMap block) {
        if (block.getString("name").equals("minecraft:unknown")) {
            return -2; // This is special case
        }
        // Order required
        TreeMap<String, Object> states = new TreeMap<>(block.getCompound("states"));
        NbtMapBuilder statesBuilder = NbtMap.builder();
        statesBuilder.putAll(states);

        NbtMap tag = NbtMap.builder()
                .putString("name", block.getString("name"))
                .putCompound("states", statesBuilder.build())
                .build();

        byte[] bytes;
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             NBTOutputStream outputStream = NbtUtils.createWriterLE(stream)) {
            outputStream.writeTag(tag);
            bytes = stream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fnv1a_32(bytes);
    }

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;

    private static int fnv1a_32(byte[] data) {
        int hash = FNV1_32_INIT;
        for (byte datum : data) {
            hash ^= (datum & 0xff);
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }

    @Value
    private static class BlockState {
        private Object val;
        private int type;
    }
}
