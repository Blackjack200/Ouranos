package com.github.blackjack200.ouranos.network.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakUnhandledMessagesQueue;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.List;

@Log4j2
public class CustomPeer extends BedrockPeer {
    public CustomPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
        this.channel.pipeline().replace(BedrockBatchDecoder.class, BatchDecoder.NAME, new BatchDecoder());
        this.channel.pipeline().replace(BedrockBatchEncoder.class, BatchEncoder.NAME, new BatchEncoder());
        var rakChannel = (RakChannel) this.getChannel();
        if (rakChannel.rakPipeline().get(RakUnhandledMessagesQueue.class) != null) {
            rakChannel.rakPipeline().replace(RakUnhandledMessagesQueue.class, RakUnhandledMessagesQueue.NAME, new RakUnhandledMessagesQueueOverride(rakChannel));
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.sessions.put(0, this.sessionFactory.createSession(this, 0));
        //this.tickFuture = this.channel.eventLoop().scheduleAtFixedRate(this::onTick, 0, 1, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendPacket(int senderClientId, int targetClientId, BedrockPacket packet) {
        super.sendPacketImmediately(senderClientId, targetClientId, packet);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof BedrockPacketWrapper) {
                this.onBedrockPacket((BedrockPacketWrapper) msg);
            } else if (msg instanceof List<?> wrapper) {
                log.debug("batch: {}", wrapper.size());
                for (var o : wrapper) {
                    this.channel.pipeline().fireChannelRead(o);
                }
            } else {
                throw new DecoderException("Unexpected message type: " + msg.getClass().getName());
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
