package com.github.blackjack200.ouranos.network.session;

import cn.hutool.core.collection.ConcurrentHashSet;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;

import java.security.KeyPair;

@Log4j2
public class OuranosProxySession {
    public static ConcurrentHashSet<OuranosProxySession> ouranosPlayers = new ConcurrentHashSet<>();
    public final ProxyClientSession upstream;
    public final ProxyServerSession downstream;
    public boolean blockNetworkIdAreHashes = false;
    @Getter
    private final KeyPair keyPair;
    public int lastFormId = -1;
    public long uniqueEntityId;
    public long runtimeEntityId;
    public boolean lastPunchAir = false;
    public AuthData identity;

    public OuranosProxySession(KeyPair keyPair, ProxyClientSession upstreamSession, ProxyServerSession downstreamSession) {
        this.keyPair = keyPair;
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
        OuranosProxySession.ouranosPlayers.add(this);
        this.downstream.addDisconnectListener(this::disconnect);
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


    public boolean isAlive() {
        return this.upstream.getCodec() != null && this.downstream.getCodec() != null && this.upstream.isConnected() && this.downstream.isConnected();
    }

    @SneakyThrows
    public void disconnect(String reason, boolean hideReason) {
        this.downstream.setPacketHandler(null);
        this.upstream.setPacketHandler(null);
        this.downstream.setPacketRedirect(null);
        this.upstream.setPacketRedirect(null);
        if (this.downstream.isConnected()) {
            if (OuranosProxySession.ouranosPlayers.contains(this)) {
                log.info("{}[{}] disconnected due to {}", this.identity.displayName(), this.downstream.getPeer().getSocketAddress(), reason);
            }
            this.downstream.disconnect(reason, hideReason);
        }
        if (this.upstream.isConnected()) {
            this.upstream.disconnect(reason, hideReason);
        }
        OuranosProxySession.ouranosPlayers.remove(this);
    }

    public void disconnect(String reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
