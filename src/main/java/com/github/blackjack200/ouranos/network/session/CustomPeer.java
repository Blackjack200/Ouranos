package com.github.blackjack200.ouranos.network.session;

import com.github.blackjack200.ouranos.Ouranos;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakUnhandledMessagesQueue;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

@Log4j2
public class CustomPeer extends BedrockPeer {

    private final boolean packetBuffering;

    public CustomPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
        var rakChannel = (RakChannel) this.getChannel();
        this.packetBuffering = Ouranos.getOuranos().getConfig().packet_buffering;
        this.getChannel().pipeline().addLast(new ChannelHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {

            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {

            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (!ctx.channel().isActive()) {
                    return;
                }
                CustomPeer.this.close(cause.getLocalizedMessage());
                log.warn("Exception in CustomPeer.exceptionCaught", cause);
            }
        });
        if (rakChannel.rakPipeline().get(RakUnhandledMessagesQueue.class) != null) {
            rakChannel.rakPipeline().replace(RakUnhandledMessagesQueue.class, RakUnhandledMessagesQueue.NAME, new RakUnhandledMessagesQueueOverride(rakChannel));
        }
    }

    @Override
    public void sendPacket(int senderClientId, int targetClientId, BedrockPacket packet) {
        if (this.packetBuffering) {
            super.sendPacket(senderClientId, targetClientId, packet);
        } else {
            super.sendPacketImmediately(senderClientId, targetClientId, packet);
        }
    }
}
