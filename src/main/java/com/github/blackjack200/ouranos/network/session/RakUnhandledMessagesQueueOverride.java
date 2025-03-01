package com.github.blackjack200.ouranos.network.session;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.packet.EncapsulatedPacket;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakUnhandledMessagesQueue;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class RakUnhandledMessagesQueueOverride extends RakUnhandledMessagesQueue {
    public static final String NAME = "rak-unhandled-messages-queue";

    private final RakChannel channel;
    private final Queue<EncapsulatedPacket> messages = PlatformDependent.newMpscQueue();
    private ScheduledFuture<?> future;

    public RakUnhandledMessagesQueueOverride(RakChannel channel) {
        super(channel);
        this.channel = channel;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.future = ctx.channel().eventLoop().scheduleAtFixedRate(() -> this.trySendMessages(ctx),
                0, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (this.future != null) {
            this.future.cancel(false);
            this.future = null;
        }

        EncapsulatedPacket message;
        while ((message = this.messages.poll()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    private void trySendMessages(ChannelHandlerContext ctx) {
        if (!this.channel.isActive()) {
            return;
        }

        EncapsulatedPacket message;
        while ((message = this.messages.poll()) != null) {
            ctx.fireChannelRead(message);
        }

        ctx.pipeline().remove(this);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EncapsulatedPacket msg) {
        if (!this.channel.isActive()) {
            this.messages.offer(msg.retain());
            return;
        }

        this.trySendMessages(ctx);
        ctx.fireChannelRead(msg.retain());
    }
}
