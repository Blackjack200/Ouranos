package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
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

    @Override
    public PacketSignal handle(TextPacket pk) {
        pk.setSourceName(session.identity.displayName());
        pk.setXuid(session.identity.xuid());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        InputMode inputMode = packet.getInputMode();
        if (inputMode != null) {
            session.movement.inputMode = inputMode;
        }
        return PacketSignal.HANDLED;
    }
}
