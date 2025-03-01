package com.github.blackjack200.ouranos.network.session;

import com.github.blackjack200.ouranos.Ouranos;
import io.netty.channel.Channel;
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
        rakChannel.rakPipeline().replace(RakUnhandledMessagesQueue.class, RakUnhandledMessagesQueue.NAME, new RakUnhandledMessagesQueueOverride(rakChannel));
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
