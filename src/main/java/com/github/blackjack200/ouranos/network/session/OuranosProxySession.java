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
    public boolean lastPunchAir = false;
    public AuthData identity;

    public OuranosProxySession(KeyPair keyPair, ProxyClientSession upstreamSession, ProxyServerSession downstreamSession) {
        this.keyPair = keyPair;
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
        OuranosProxySession.ouranosPlayers.add(this);
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
        return this.upstream.isConnected() && this.downstream.isConnected();
    }

    @SneakyThrows
    public void disconnect(String reason, boolean hideReason) {
        OuranosProxySession.ouranosPlayers.remove(this);
        if (this.downstream.isConnected()) {
            try {
                this.downstream.disconnect(reason, hideReason);
                this.downstream.getPeer().getChannel().flush().closeFuture().get();
                this.downstream.close(reason);
                this.downstream.setPacketHandler(new BedrockPacketHandler() {
                });
            } catch (Throwable throwable) {
                //  log.debug(throwable);
            }
        }
        if (this.upstream.isConnected()) {
            try {
                this.upstream.disconnect(reason, hideReason);
                this.upstream.getPeer().getChannel().flush().closeFuture().get();
                this.upstream.close(reason);
                this.downstream.setPacketHandler(new BedrockPacketHandler() {
                });
            } catch (Throwable throwable) {
                //  log.debug(throwable);
            }
        }
    }

    public void disconnect(String reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
