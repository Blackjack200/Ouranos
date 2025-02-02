package com.github.blackjack200.ouranos.network.convert;

import com.github.blackjack200.ouranos.network.convert.palette.Palette;
import io.netty.buffer.ByteBuf;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.common.util.TriFunction;

@Log4j2
@UtilityClass
public class PaletteStorage {
    public static void translatePaletteStorage(int input, int output, ByteBuf from, ByteBuf to, TriFunction<Integer, Integer, Integer, Integer> rewriter) throws ChunkRewriteException {
        var storage = new Palette<Integer>();
        storage.readNetwork(from, (id) -> id);
        storage.writeNetwork(to, (id) -> rewriter.apply(input, output, id));
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
