package com.blackjack200.ouranos.network.session;

import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;

import java.security.KeyPair;

public class OuranosPlayer {
    private final BedrockClientSession upstream;
    private final BedrockServerSession downstream;
    @Getter
    private final KeyPair keyPair = EncryptionUtils.createKeyPair();

    public OuranosPlayer(BedrockClientSession upstreamSession, BedrockServerSession downstreamSession) {
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
    }

    public int getUpstreamProtocolId() {
        return this.upstream.getCodec().getProtocolVersion();
    }

    public int getDownstreamProtocolId() {
        return this.downstream.getCodec().getProtocolVersion();
    }

    public void setUpstreamHandler(BedrockPacketHandler handler) {
        this.upstream.setPacketHandler(handler);
    }

    public void setDownstreamHandler(BedrockPacketHandler handler) {
        this.downstream.setPacketHandler(handler);
    }

    public void disconnect(String reason, boolean hideReason) {
        if (this.upstream.isConnected()) {
            this.upstream.disconnect(reason, hideReason);
            this.upstream.close(reason);
        }
        if (this.downstream.isConnected()) {
            this.downstream.disconnect(reason, hideReason);
        }
    }

    public void disconnect(String reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
