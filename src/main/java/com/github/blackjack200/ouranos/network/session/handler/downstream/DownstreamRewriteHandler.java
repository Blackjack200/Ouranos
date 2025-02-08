package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.common.PacketSignal;

@Log4j2
public class DownstreamRewriteHandler implements BedrockPacketHandler {
    private final OuranosProxySession session;

    public DownstreamRewriteHandler(OuranosProxySession session) {
        this.session = session;
    }

    @Override
    public void onDisconnect(String reason) {
        this.session.disconnect(reason);
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        this.session.disconnect(packet.getKickMessage(), packet.isMessageSkipped());
        return PacketSignal.HANDLED;
    }
}
