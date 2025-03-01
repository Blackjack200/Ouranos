package com.github.blackjack200.ouranos.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.netty.channel.raknet.RakPong;
import org.cloudburstmc.protocol.bedrock.BedrockPong;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ClientPingHandler extends ChannelDuplexHandler {

    private final Consumer<BedrockPong> consumer;

    private final long timeout;
    private final TimeUnit timeUnit;
    private ScheduledFuture<?> timeoutFuture;

    public ClientPingHandler(Consumer<BedrockPong> consumer, long timeout, TimeUnit timeUnit) {
        this.consumer = consumer;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    private void onTimeout(Channel channel) {
        channel.close();
        this.consumer.accept(null);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.timeoutFuture = ctx.channel().eventLoop().schedule(() -> this.onTimeout(ctx.channel()), this.timeout, this.timeUnit);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof RakPong pong)) {
            super.channelRead(ctx, msg);
            return;
        }

        if (this.timeoutFuture != null) {
            this.timeoutFuture.cancel(false);
            this.timeoutFuture = null;
        }

        ctx.channel().close();
        ByteBuf data = pong.getPongData();
        this.consumer.accept(BedrockPong.fromRakNet(data));
        ReferenceCountUtil.release(data);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);

        if (this.timeoutFuture != null) {
            this.timeoutFuture.cancel(false);
            this.timeoutFuture = null;
        }
    }
}