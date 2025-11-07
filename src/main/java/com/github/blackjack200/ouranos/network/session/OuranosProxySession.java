package com.github.blackjack200.ouranos.network.session;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.github.blackjack200.ouranos.network.session.translate.InventoryData;
import com.github.blackjack200.ouranos.network.session.translate.MovementData;
import io.netty.util.concurrent.ScheduledFuture;
import java.security.KeyPair;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;

@Log4j2
public class OuranosProxySession {

    private static final ProxySessionRegistry REGISTRY =
        ProxySessionRegistry.instance();

    private final ProxyClientSession upstream;
    private final ProxyServerSession downstream;
    private ScheduledFuture<?> fut;
    private boolean blockNetworkIdAreHashes = false;

    @Getter
    private final KeyPair keyPair;

    private int lastFormId = -1;
    private long uniqueEntityId;
    private long runtimeEntityId;
    private final MovementData movement = new MovementData();
    private final InventoryData inventory = new InventoryData();
    private AuthData identity;

    @Getter
    private int downstreamProtocolId;

    @Getter
    private int upstreamProtocolId;

    public OuranosProxySession(
        KeyPair keyPair,
        ProxyClientSession upstreamSession,
        ProxyServerSession downstreamSession
    ) {
        this.keyPair = keyPair;
        this.upstream = upstreamSession;
        this.downstream = downstreamSession;
        REGISTRY.register(this);
        this.downstream.addDisconnectListener(reason ->
            this.disconnect(reason, false)
        );
        this.upstream.addDisconnectListener(reason ->
            this.disconnect(reason, false)
        );
        this.downstreamProtocolId =
            this.downstream.getCodec().getProtocolVersion();
        this.upstreamProtocolId = this.upstream.getCodec().getProtocolVersion();
    }

    public ProxyClientSession getUpstream() {
        return upstream;
    }

    public ProxyServerSession getDownstream() {
        return downstream;
    }

    public MovementData movement() {
        return movement;
    }

    public InventoryData inventory() {
        return inventory;
    }

    public AuthData getIdentity() {
        return identity;
    }

    public void setIdentity(AuthData identity) {
        this.identity = identity;
    }

    public boolean isBlockNetworkIdAreHashes() {
        return blockNetworkIdAreHashes;
    }

    public void setBlockNetworkIdAreHashes(boolean blockNetworkIdAreHashes) {
        this.blockNetworkIdAreHashes = blockNetworkIdAreHashes;
    }

    public int getLastFormId() {
        return lastFormId;
    }

    public void setLastFormId(int lastFormId) {
        this.lastFormId = lastFormId;
    }

    public long getUniqueEntityId() {
        return uniqueEntityId;
    }

    public void setUniqueEntityId(long uniqueEntityId) {
        this.uniqueEntityId = uniqueEntityId;
    }

    public long getRuntimeEntityId() {
        return runtimeEntityId;
    }

    public void setRuntimeEntityId(long runtimeEntityId) {
        this.runtimeEntityId = runtimeEntityId;
    }

    public List<BedrockPacket> tickMovement() {
        return List.of(this.movement.tick(this.getUpstreamProtocolId(), this));
    }

    public void setUpstreamHandler(BedrockPacketHandler handler) {
        this.upstream.setPacketHandler(handler);
    }

    public void setDownstreamHandler(BedrockPacketHandler handler) {
        this.downstream.setPacketHandler(handler);
    }

    public boolean isAlive() {
        return (
            this.upstream.getCodec() != null &&
            this.downstream.getCodec() != null &&
            this.upstream.isConnected() &&
            this.downstream.isConnected()
        );
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
            if (REGISTRY.contains(this)) {
                var name = this.identity != null
                    ? this.identity.displayName()
                    : "";
                log.info(
                    "{}[{}] disconnected due to {}",
                    name,
                    this.downstream.getPeer().getSocketAddress(),
                    reason
                );
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
        REGISTRY.unregister(this);
    }

    public void disconnect(CharSequence reason) {
        this.disconnect(reason, false);
    }

    public void disconnect() {
        this.disconnect("disconnect.disconnected", false);
    }
}
