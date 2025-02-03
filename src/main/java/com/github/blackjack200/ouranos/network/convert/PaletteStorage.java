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

}
