package com.github.blackjack200.ouranos.network.session;

import lombok.Getter;
import lombok.SneakyThrows;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;

import java.security.KeyPair;
import java.util.HashSet;
import java.util.Set;

public class OuranosPlayer {
    public static Set<OuranosPlayer> ouranosPlayers = new HashSet<>();
    public final BedrockClientSession upstream;
    public final BedrockServerSession downstream;
    public boolean blockNetworkIdAreHashes = false;
    @Getter
    private final KeyPair keyPair = EncryptionUtils.createKeyPair();

    public OuranosPlayer(BedrockClientSession upstreamSession, BedrockServerSession downstreamSession) {
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
        OuranosPlayer.ouranosPlayers.add(this);
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

    @SneakyThrows
    public void disconnect(String reason, boolean hideReason) {
        OuranosPlayer.ouranosPlayers.remove(this);
        if (this.downstream.isConnected()) {
            this.downstream.disconnect(reason, hideReason);
            this.downstream.getPeer().getChannel().flush().closeFuture().get();
            this.downstream.close(reason);
        }
        if (this.upstream.isConnected()) {
            this.upstream.disconnect(reason, hideReason);
        }
    }

    public void disconnect(String reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
