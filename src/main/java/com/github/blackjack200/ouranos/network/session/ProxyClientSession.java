package com.github.blackjack200.ouranos.network.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Log4j2
public class ProxyClientSession extends BedrockClientSession {
    @Getter
    @Setter
    public volatile BiConsumer<ProxyClientSession, BedrockPacket> packetRedirect = null;

    public ProxyClientSession(CustomPeer peer, int subClientId) {
        super(peer, subClientId);
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();

        try {
            if (this.packetHandler != null) {
                this.packetHandler.handlePacket(packet);
            }
            if (this.isConnected() && this.peer.isConnected() && this.packetRedirect != null) {
                this.packetRedirect.accept(this, wrapper.getPacket());
            }
        } catch (DropPacketException ignored) {
        }
    }

    public void addDisconnectListener(Consumer<CharSequence> listener) {
        this.getPeer().getChannel().closeFuture().addListener(future -> listener.accept(this.getDisconnectReason()));
    }
}
