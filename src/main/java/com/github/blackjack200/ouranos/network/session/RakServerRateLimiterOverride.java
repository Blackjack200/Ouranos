package com.github.blackjack200.ouranos.network.session;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Log4j2
public class RakServerRateLimiterOverride extends RakServerRateLimiter {
    public static final String NAME = "ouranos-rak-server-rate-limiter";

    public RakServerRateLimiterOverride(RakServerChannel channel) {
        super(channel);
    }

    @Override
    protected int getAddressMaxPacketCount(InetAddress address) {
        var sessionCount = (int) OuranosProxySession.ouranosPlayers.stream().filter((s) -> ((InetSocketAddress) s.upstream.getSocketAddress()).getAddress().equals(address)).count() + 2;
        return super.getAddressMaxPacketCount(address) * sessionCount;
    }
}