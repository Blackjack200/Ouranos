package com.blackjack200.ouranos.network.session;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;

@Log4j2
public class UpstreamSession extends BedrockClientSession {
    public DownstreamSession downstream;

    public UpstreamSession(BedrockPeer peer, int subClientId) {
        super(peer, subClientId);
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();
        this.logInbound(packet);
        if (packetHandler == null) {
            log.warn("Received packet without a packet handler for {}:{}: {}", this.getSocketAddress(), this.subClientId, packet);
        } else if (this.packetHandler.handlePacket(packet) == PacketSignal.UNHANDLED) {
            log.warn("Unhandled packet for {}:{}: {}", this.getSocketAddress(), this.subClientId, packet);
        }
        if (this.downstream != null) {
            this.downstream.sendPacket(packet);
        }
    }
}
