package com.github.blackjack200.ouranos.network.session;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class BatchDecoder extends BedrockBatchDecoder {
    public static final String NAME = "bedrock-batch-decoder";

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) {
        if (msg.getUncompressed() == null) {
            throw new IllegalStateException("Batch packet was not decompressed");
        }

        ByteBuf buffer = msg.getUncompressed().slice();
        var list = new ArrayList<ByteBuf>();
        while (buffer.isReadable()) {
            int packetLength = VarInts.readUnsignedInt(buffer);
            ByteBuf packetBuf = buffer.readRetainedSlice(packetLength);
            list.add(packetBuf);
        }
        out.add(list);
    }
}
