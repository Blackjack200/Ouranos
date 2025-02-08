package com.github.blackjack200.ouranos.network.session.handler.upstream;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.Translate;
import com.github.blackjack200.ouranos.network.session.handler.downstream.DownstreamRewriteHandler;
import com.github.blackjack200.ouranos.utils.BlockDictionaryRegistry;
import com.github.blackjack200.ouranos.utils.ItemTypeDictionaryRegistry;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;

@Log4j2
public class UpstreamInitialHandler implements BedrockPacketHandler {
    private final OuranosProxySession session;
    private final LoginPacket loginPacket;

    public UpstreamInitialHandler(OuranosProxySession session, LoginPacket loginPacket) {
        this.session = session;
        this.loginPacket = loginPacket;

        val packet = new RequestNetworkSettingsPacket();
        packet.setProtocolVersion(session.getUpstreamProtocolId());
        session.upstream.sendPacketImmediately(packet);
    }

    @Override
    public void onDisconnect(String reason) {
        this.session.disconnect(reason);
    }

    @Override
    public PacketSignal handle(DisconnectPacket pk) {
        this.session.disconnect(pk.getKickMessage(), pk.isMessageSkipped());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket pkk) {
        this.session.upstream.setCompression(pkk.getCompressionAlgorithm());
        this.session.upstream.sendPacketImmediately(this.loginPacket);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket pk) {
        this.session.upstream.getPeer().getChannel().eventLoop().execute(() -> {
            try {
                var jws = new JsonWebSignature();
                jws.setCompactSerialization(pk.getJwt());
                var saltJwt = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));
                var x5u = jws.getHeader(HeaderParameterNames.X509_URL);
                var serverKey = EncryptionUtils.parseKey(x5u);
                var key = EncryptionUtils.getSecretKey(this.session.getKeyPair().getPrivate(), serverKey,
                        Base64.getDecoder().decode(JsonUtils.childAsType(saltJwt, "salt", String.class)));
                this.session.upstream.enableEncryption(key);
            } catch (JoseException | NoSuchAlgorithmException | InvalidKeySpecException |
                     InvalidKeyException e) {
                throw new RuntimeException(e);
            }
            val handshake = new ClientToServerHandshakePacket();
            this.session.upstream.sendPacketImmediately(handshake);
        });
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PlayStatusPacket packet) {
        if (packet.getStatus().equals(PlayStatusPacket.Status.LOGIN_SUCCESS)) {
            setupRedirect();
        }
        return PacketSignal.HANDLED;
    }

    private void setupRedirect() {
        session.downstream.setPacketRedirect((_upstream, packet) -> {
            ReferenceCountUtil.retain(ReferenceCountUtil.touch(packet));
            if (session.upstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                var packets = Translate.translate(session.getDownstreamProtocolId(), session.getUpstreamProtocolId(), session, packet);
                for (var pk : packets) {
                    if (Ouranos.getOuranos().getConfig().debug && !(pk instanceof PlayerAuthInputPacket)) {
                        log.debug("C->S {}", pk.getClass());
                    }
                    session.upstream.sendPacket(pk);
                }
            }
        });
        session.upstream.setPacketRedirect((_downstream, packet) -> {
            ReferenceCountUtil.retain(ReferenceCountUtil.touch(packet));
            if (session.downstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                var packets = Translate.translate(session.getUpstreamProtocolId(), session.getDownstreamProtocolId(), session, packet);
                for (var pk : packets) {
                    if (Ouranos.getOuranos().getConfig().debug && !(pk instanceof PlayerAuthInputPacket)) {
                        log.debug("S->C {}", pk.getClass());
                    }
                    session.downstream.sendPacket(pk);
                }
            }
        });
        session.downstream.setPacketHandler(new DownstreamRewriteHandler(this.session));
    }

    @Override
    public PacketSignal handle(StartGamePacket pk) {
        var upstreamProtocolId = this.session.getUpstreamProtocolId();
        var downstreamProtocolId = this.session.getDownstreamProtocolId();
        this.session.upstream.getPeer().getCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(upstreamProtocolId));
        this.session.downstream.getPeer().getCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(downstreamProtocolId));

        List<ItemDefinition> def = ItemTypeDictionary.getInstance(downstreamProtocolId).getEntries().entrySet().stream().<ItemDefinition>map((e) -> new SimpleItemDefinition(e.getKey(), e.getValue().runtime_id(), e.getValue().component_based())).toList();
        pk.setItemDefinitions(def);

        this.session.upstream.getPeer().getCodecHelper().setBlockDefinitions(new BlockDictionaryRegistry(upstreamProtocolId));
        this.session.downstream.getPeer().getCodecHelper().setBlockDefinitions(new BlockDictionaryRegistry(downstreamProtocolId));

        this.session.blockNetworkIdAreHashes = pk.isBlockNetworkIdsHashed();
        pk.setServerEngine("Ouranos"); //for telemetry

        this.session.downstream.sendPacketImmediately(pk);
        return PacketSignal.HANDLED;
    }
}
