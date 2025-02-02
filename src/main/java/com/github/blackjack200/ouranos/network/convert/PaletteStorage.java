package com.github.blackjack200.ouranos.network.convert;

import io.netty.buffer.ByteBuf;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.common.util.TriFunction;
import org.cloudburstmc.protocol.common.util.VarInts;

@Log4j2
@UtilityClass
public class PaletteStorage {
    public static void translatePaletteStorage(int input, int output, ByteBuf from, ByteBuf to, TriFunction<Integer, Integer, Integer, Integer> rewriter) throws ChunkRewriteException {
        var blockSize = from.readUnsignedByte() >> 1;
        log.debug("block size: {}", blockSize);
        if (blockSize == 0x7f) {
            //equals with previous
            return;
        }
        if (blockSize > 32) {
            throw new ChunkRewriteException("Cannot read palette storage (size=" + blockSize + "): size too large");
        }
        //1 for isNonPersistent
        to.writeByte((blockSize << 1) | 1);
        val uint32Count = uint32s(blockSize);
        val byteCount = uint32Count * 4;
        log.debug("uint32Count: {}", uint32Count);
        log.debug("byteCount: {}", byteCount);
        to.writeBytes(from, byteCount);

        var paletteCount = 1;
        if (blockSize != 0) {
            paletteCount = VarInts.readInt(from);
            if (paletteCount <= 0) {
                throw new ChunkRewriteException("Invalid palette entry count " + paletteCount);
            }
        }
        log.debug("paletteCount: {}", paletteCount);
        VarInts.writeInt(to, paletteCount);

        for (var i = 0; i < paletteCount; i++) {
            int runtimeId = VarInts.readInt(from);
            VarInts.writeInt(to, runtimeId);
        }
    }

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
}
