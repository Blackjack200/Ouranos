package com.github.blackjack200.ouranos.network.session;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;

import java.security.KeyPair;
import java.util.Vector;

@Log4j2
public class OuranosProxySession {
    public static Vector<OuranosProxySession> ouranosPlayers = new Vector<>();
    public final ProxyClientSession upstream;
    public final ProxyServerSession downstream;
    public boolean blockNetworkIdAreHashes = false;
    @Getter
    private final KeyPair keyPair;

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
                log.debug(throwable);
            }
        }
        if (this.upstream.isConnected()) {
            this.upstream.disconnect(reason, hideReason);
            this.upstream.getPeer().getChannel().flush().closeFuture().get();
            this.upstream.close(reason);
            this.downstream.setPacketHandler(new BedrockPacketHandler() {
            });
        }
    }

    public void disconnect(String reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
