package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.network.session.DropPacketException;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.*;
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
    public PacketSignal handle(LevelSoundEventPacket packet) {
        throw new DropPacketException();
    }
}
