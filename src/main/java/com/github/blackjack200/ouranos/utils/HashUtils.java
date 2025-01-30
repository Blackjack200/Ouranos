package com.github.blackjack200.ouranos.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.TreeMap;

/**
 * Hash utilities.
 *
 * @author Cool_Loong | daoge_cmd
 */
@Log4j2
@UtilityClass
public class HashUtils {
    //https://gist.github.com/Alemiz112/504d0f79feac7ef57eda174b668dd345
    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;

    /**
     * Compute block state hash from the given identifier and property values.
     *
     * @param identifier     the identifier.
     * @param propertyValues the property values.
     * @return the hash.
     */
    public int computeBlockStateHash(String identifier, NbtMap propertyValues) {
        if (identifier.equals("minecraft:unknown")) {
            return -2; // This is special case
        }

        var states = new TreeMap<>(propertyValues.getCompound("states", propertyValues));

        var mappedFullState = BlockStateUpdaters.updateBlockState(NbtMap.builder()
                        .putString("name", identifier)
                        .putCompound("states", NbtMap.fromMap(states))
                        .build()
                , BlockStateUpdaters.LATEST_VERSION);
        return fnv1a_32_nbt(mappedFullState);
    }

    public int computeBlockStateHash(NbtMap propertyValues) {
        val identifier = propertyValues.getString("name");
        return computeBlockStateHash(identifier, propertyValues);
    }

    /**
     * FNV-1a 32-bit hash algorithm.
     *
     * @param tag the tag to hash.
     * @return the hash.
     */
    public int fnv1a_32_nbt(NbtMap tag) {
        byte[] bytes;
        try (var stream = new ByteArrayOutputStream();
             var outputStream = NbtUtils.createWriterLE(stream)) {
            outputStream.writeTag(tag);
            bytes = stream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to covert NBT into bytes", e);
            throw new RuntimeException(e);
        }

        return fnv1a_32(bytes);
    }

    /**
     * FNV-1a 32-bit hash algorithm.
     *
     * @param data the data to hash.
     * @return the hash.
     */
    public int fnv1a_32(byte[] data) {
        int hash = FNV1_32_INIT;
        for (byte datum : data) {
            hash ^= (datum & 0xff);
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }
}
