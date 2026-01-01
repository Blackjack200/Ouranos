package com.github.blackjack200.ouranos.network.session;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.github.blackjack200.ouranos.network.session.translate.InventoryData;
import com.github.blackjack200.ouranos.network.session.translate.MovementData;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class OuranosProxySession {
    public static ConcurrentHashSet<OuranosProxySession> ouranosPlayers = new ConcurrentHashSet<>();
    public final ProxyClientSession upstream;
    public final ProxyServerSession downstream;
    private ScheduledFuture<?> fut;
    public boolean blockNetworkIdAreHashes = false;
    @Getter
    private final KeyPair keyPair;
    public int lastFormId = -1;
    public long uniqueEntityId;
    public long runtimeEntityId;
    public volatile int currentDimension = 0;
    public MovementData movement = new MovementData();
    public InventoryData inventory = new InventoryData();
    public final Map<Byte, ContainerType> openContainers = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private boolean serverAuthoritativeInventories;
    public AuthData identity;
    @Getter
    private int downstreamProtocolId;
    @Getter
    private int upstreamProtocolId;

    public List<BedrockPacket> tickMovement() {
        return List.of(this.movement.tick(this.getUpstreamProtocolId(), this));
    }

    public OuranosProxySession(KeyPair keyPair, ProxyClientSession upstreamSession, ProxyServerSession downstreamSession) {
        this.keyPair = keyPair;
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
        OuranosProxySession.ouranosPlayers.add(this);
        this.downstream.addDisconnectListener(this::disconnect);
        this.upstream.addDisconnectListener(this::disconnect);
        this.downstreamProtocolId = this.downstream.getCodec().getProtocolVersion();
        this.upstreamProtocolId = this.upstream.getCodec().getProtocolVersion();
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
    public void disconnect(CharSequence reason, boolean hideReason) {
        this.downstream.setPacketHandler(null);
        this.upstream.setPacketHandler(null);
        this.downstream.setPacketRedirect(null);
        this.upstream.setPacketRedirect(null);
        if (this.fut != null) {
            this.fut.cancel(true);
            this.fut = null;
        }
        if (this.downstream.isConnected()) {
            if (OuranosProxySession.ouranosPlayers.contains(this)) {
                String name = "";
                if (this.identity != null) {
                    name = this.identity.displayName();
                }
                log.info("{}[{}] disconnected due to {}", name, this.downstream.getPeer().getSocketAddress(), reason);
            }
            try {
                this.downstream.disconnect(reason, hideReason);
            } catch (Throwable e) {
                log.error(e);
            }
        }
        if (this.upstream.isConnected()) {
            try {
                this.upstream.disconnect(reason, hideReason);
            } catch (Throwable e) {
                log.error(e);
            }
        }
        OuranosProxySession.ouranosPlayers.remove(this);
    }

    public void disconnect(CharSequence reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }

}
