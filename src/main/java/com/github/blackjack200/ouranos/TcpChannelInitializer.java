package com.github.blackjack200.ouranos;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.*;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v1;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v2;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.common.util.Zlib;

@Log4j2
public abstract class TcpChannelInitializer<T extends BedrockSession> extends ChannelInitializer<Channel> {
    public static final int RAKNET_MINECRAFT_ID = 0xFE;
    private static final FrameIdCodec RAKNET_FRAME_CODEC = new FrameIdCodec(RAKNET_MINECRAFT_ID);
    private static final BedrockBatchDecoder BATCH_DECODER = new BedrockBatchDecoder();

    private static final String FRAME_DECODER = "tcp-frame-decoder";
    private static final String FRAME_ENCODER = "tcp-frame-encoder";

    private static final CompressionStrategy ZLIB_RAW_STRATEGY = new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW));
    private static final CompressionStrategy ZLIB_STRATEGY = new SimpleCompressionStrategy(new ZlibCompression(Zlib.DEFAULT));
    private static final CompressionStrategy SNAPPY_STRATEGY = new SimpleCompressionStrategy(new SnappyCompression());
    private static final CompressionStrategy NOOP_STRATEGY = new SimpleCompressionStrategy(new NoopCompression());

    @Override
    protected final void initChannel(Channel channel) throws Exception {
        this.preInitChannel(channel);

        channel.pipeline()
                .addLast(BedrockBatchDecoder.NAME, BATCH_DECODER)
                .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder());

        this.initPacketCodec(channel);

        channel.pipeline().addLast(BedrockPeer.NAME, this.createPeer(channel));

        this.postInitChannel(channel);
        log.info(channel.pipeline().toString());
    }

    protected void preInitChannel(Channel channel) throws Exception {
        channel.pipeline()
                .addLast(FRAME_DECODER, new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                .addLast(FRAME_ENCODER, new LengthFieldPrepender(4));

        channel.pipeline().addLast(FrameIdCodec.NAME, RAKNET_FRAME_CODEC);

        int rakVersion = getRakVersion();

        CompressionStrategy compression = getCompression(PacketCompressionAlgorithm.ZLIB, rakVersion, true);
        // At this point all connections use not prefixed compression
        channel.pipeline().addLast(CompressionCodec.NAME, new CompressionCodec(compression, false));
    }

    public static CompressionStrategy getCompression(CompressionAlgorithm algorithm, int rakVersion, boolean initial) {
        switch (rakVersion) {
            case 7:
            case 8:
            case 9:
                return ZLIB_STRATEGY;
            case 10:
                return ZLIB_RAW_STRATEGY;
            case 11:
                return initial ? NOOP_STRATEGY : getCompression(algorithm);
            default:
                throw new UnsupportedOperationException("Unsupported RakNet protocol version: " + rakVersion);
        }
    }

    private static CompressionStrategy getCompression(CompressionAlgorithm algorithm) {
        if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            return ZLIB_RAW_STRATEGY;
        } else if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return SNAPPY_STRATEGY;
        } else if (algorithm == PacketCompressionAlgorithm.NONE) {
            return NOOP_STRATEGY;
        } else {
            throw new UnsupportedOperationException("Unsupported compression algorithm: " + algorithm);
        }
    }

    protected void postInitChannel(Channel channel) throws Exception {
    }

    protected void initPacketCodec(Channel channel) throws Exception {
        int rakVersion = getRakVersion();

        switch (rakVersion) {
            case 11:
            case 10:
            case 9: // Merged & Varint-ified
                channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
                break;
            case 8: // Split-screen support
                channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v2());
                break;
            case 7: // Single byte packet ID
                channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v1());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported RakNet protocol version: " + rakVersion);
        }
    }

    protected BedrockPeer createPeer(Channel channel) {
        return new BedrockPeer(channel, this::createSession);
    }

    protected final T createSession(BedrockPeer peer, int subClientId) {
        T session = this.createSession0(peer, subClientId);
        this.initSession(session);
        return session;
    }

    protected abstract T createSession0(BedrockPeer peer, int subClientId);

    protected abstract void initSession(T session);

    protected abstract int getRakVersion();
}

