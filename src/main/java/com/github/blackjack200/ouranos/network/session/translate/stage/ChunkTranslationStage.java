package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.convert.ChunkRewriteException;
import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;

@Log4j2
public final class ChunkTranslationStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }
        if (packet instanceof LevelChunkPacket pk) {
            rewriteFullChunk(context, pk);
        } else if (packet instanceof SubChunkPacket pk) {
            rewriteSubChunks(context, pk);
        }
    }

    private static void rewriteFullChunk(
        TranslationContext context,
        LevelChunkPacket packet
    ) {
        var from = packet.getData();
        var to = AbstractByteBufAllocator.DEFAULT.buffer(
            from.readableBytes()
        ).touch();
        try {
            var newSubChunkCount = TypeConverter.rewriteFullChunk(
                context.inputProtocol(),
                context.outputProtocol(),
                from,
                to,
                packet.getDimension(),
                packet.getSubChunksLength()
            );
            packet.setSubChunksLength(newSubChunkCount);
            packet.setData(to.retain());
        } catch (ChunkRewriteException exception) {
            log.error("Failed to rewrite chunk: ", exception);
            context
                .session()
                .disconnect(
                    "Failed to rewrite chunk: " + exception.getMessage()
                );
        } finally {
            ReferenceCountUtil.release(from);
            ReferenceCountUtil.release(to);
        }
    }

    private static void rewriteSubChunks(
        TranslationContext context,
        SubChunkPacket packet
    ) {
        var input = context.inputProtocol();
        var output = context.outputProtocol();
        for (var subChunk : packet.getSubChunks()) {
            if (subChunk.getData().readableBytes() <= 0) {
                continue;
            }
            var from = subChunk.getData();
            var to = AbstractByteBufAllocator.DEFAULT.buffer(
                from.readableBytes()
            );
            try {
                TypeConverter.rewriteSubChunk(input, output, from, to);
                TypeConverter.rewriteBlockEntities(input, output, from, to);
                to.writeBytes(from);
                subChunk.setData(to.retain());
            } catch (ChunkRewriteException exception) {
                log.error("Failed to rewrite chunk: ", exception);
                context
                    .session()
                    .disconnect(
                        "Failed to rewrite chunk: " + exception.getMessage()
                    );
            } finally {
                ReferenceCountUtil.release(from);
                ReferenceCountUtil.release(to);
            }
        }
        if (output < Bedrock_v475.CODEC.getProtocolVersion()) {
            packet.getSubChunks().subList(0, 4).clear();
        }
    }
}
