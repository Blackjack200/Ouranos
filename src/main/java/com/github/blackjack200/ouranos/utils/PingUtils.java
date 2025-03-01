package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.Ouranos;
import io.netty.channel.ChannelFutureListener;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakPing;
import org.cloudburstmc.protocol.bedrock.BedrockPong;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Log4j2
@UtilityClass
public class PingUtils {
    public void ping(Consumer<BedrockPong> consumer, InetSocketAddress address, long timeout, TimeUnit timeUnit) {
        Ouranos.getOuranos().prepareUpstreamBootstrap().handler(new ClientPingHandler(consumer, timeout, timeUnit))
                .bind(0)
                .addListener((ChannelFutureListener) fut -> {
                    if (!fut.isSuccess()) {
                        fut.channel().close();
                        consumer.accept(null);
                        return;
                    }
                    var ping = new RakPing(System.currentTimeMillis(), address);
                    fut.channel().writeAndFlush(ping);
                });
    }

    public Future<BedrockPong> ping(InetSocketAddress address, long timeout, TimeUnit timeUnit) {
        var f = new CompletableFuture<BedrockPong>();
        Ouranos.getOuranos().prepareUpstreamBootstrap().handler(new ClientPingHandler(f::complete, timeout, timeUnit))
                .bind(0)
                .addListener((ChannelFutureListener) fut -> {
                    if (!fut.isSuccess()) {
                        fut.channel().close();
                        f.completeExceptionally(fut.cause());
                        return;
                    }
                    var ping = new RakPing(System.currentTimeMillis(), address);
                    fut.channel().writeAndFlush(ping);
                });
        return f;
    }
}
