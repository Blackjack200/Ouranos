package com.github.blackjack200.ouranos.network.session.handler.downstream;

import cn.hutool.core.convert.Convert;
import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.session.AuthData;
import com.github.blackjack200.ouranos.network.session.ProxyServerSession;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;

import java.nio.charset.StandardCharsets;

@Log4j2
public class DownstreamInitialHandler implements BedrockPacketHandler {
    private final ProxyServerSession downstream;

    public DownstreamInitialHandler(ProxyServerSession session) {
        this.downstream = session;
    }

    private BedrockCodec setupCodec(int protocolId) {
        val codec = ProtocolInfo.getPacketCodec(protocolId);
        if (codec == null) {
            log.error("Protocol version {} is not supported", protocolId);
            val compact = BedrockCompat.disconnectCompat(protocolId);
            this.downstream.setCodec(compact);
            val status = new PlayStatusPacket();
            status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
            this.downstream.sendPacketImmediately(status);
            this.downstream.disconnect();
            return null;
        }
        this.downstream.setCodec(codec);
        return codec;
    }

    @Override
    @SneakyThrows
    public PacketSignal handle(LoginPacket packet) {
        val codec = setupCodec(packet.getProtocolVersion());
        if (codec == null) {
            return PacketSignal.HANDLED;
        }
        log.info("{} log-in using minecraft {} {}", this.downstream.getPeer().getSocketAddress(), codec.getMinecraftVersion(), codec.getProtocolVersion());

        var chain = EncryptionUtils.validateChain(packet.getChain());

        var claims = chain.identityClaims();
        var extraData = claims.extraData;
        var identityData = new AuthData(extraData.displayName,
                extraData.identity, extraData.xuid);

        if (identityData.xuid().isEmpty() && Ouranos.getOuranos().getConfig().online_mode) {
            this.downstream.disconnect("You need to authenticate to Xbox Live.");
            return null;
        }

        log.info("{}[{}] authorized {} {}", identityData.displayName(), this.downstream.getPeer().getSocketAddress(), codec.getMinecraftVersion(), codec.getProtocolVersion());

        var jws = new JsonWebSignature();
        jws.setKey(claims.parsedIdentityPublicKey());
        jws.setCompactSerialization(packet.getExtra());
        jws.setPayloadCharEncoding(String.valueOf(StandardCharsets.UTF_8));
        jws.verifySignature();
        var rawExtraData = Convert.toMap(String.class, Object.class, chain.rawIdentityClaims().get("extraData"));

        var clientData = new JSONObject(JsonUtil.parseJson(jws.getPayload()));

        this.downstream.setPacketHandler(new DownstreamLoginHandler(this.downstream, identityData, rawExtraData, clientData));
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        val codec = setupCodec(packet.getProtocolVersion());
        if (codec == null) {
            return PacketSignal.HANDLED;
        }
        log.info("{} requesting network setting with minecraft {} {}", this.downstream.getPeer().getSocketAddress(), codec.getProtocolVersion(), codec.getMinecraftVersion());

        val pk = new NetworkSettingsPacket();
        pk.setCompressionThreshold(0);
        pk.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);

        this.downstream.sendPacketImmediately(pk);
        this.downstream.setCompression(PacketCompressionAlgorithm.ZLIB);
        return PacketSignal.HANDLED;
    }
}
