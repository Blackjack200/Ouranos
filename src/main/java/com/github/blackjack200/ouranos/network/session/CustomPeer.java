package com.github.blackjack200.ouranos.network.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;

import java.util.concurrent.TimeUnit;

public class CustomPeer extends BedrockPeer {
    public CustomPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.sessions.put(0, this.sessionFactory.createSession(this, 0));
        this.tickFuture = this.channel.eventLoop().scheduleAtFixedRate(this::onTick, 0, 1, TimeUnit.MILLISECONDS);
    }
}
