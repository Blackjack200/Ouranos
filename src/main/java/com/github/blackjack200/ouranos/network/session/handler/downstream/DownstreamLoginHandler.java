package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.network.session.AuthData;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.ProxyClientSession;
import com.github.blackjack200.ouranos.network.session.ProxyServerSession;
import com.github.blackjack200.ouranos.network.session.handler.upstream.UpstreamInitialHandler;
import com.github.blackjack200.ouranos.utils.LoginPacketUtils;
import com.github.blackjack200.ouranos.utils.auth.Auth;
import com.github.blackjack200.ouranos.utils.auth.Xbox;
import com.github.blackjack200.ouranos.utils.auth.XboxLogin;
import io.netty.channel.ChannelFuture;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.jose4j.json.internal.json_simple.JSONObject;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;

@Log4j2
public class DownstreamLoginHandler implements BedrockPacketHandler {
    private final ProxyServerSession downstream;
    private final LoginPacket login;
    private final KeyPair keyPair = EncryptionUtils.createKeyPair();
    private final AuthData identityData;
    private final Map<String, Object> rawExtraData;
    private final JSONObject clientData;
    private String chainData;

    public DownstreamLoginHandler(ProxyServerSession downstream, LoginPacket login, AuthData identityData, Map<String, Object> rawExtraData, JSONObject clientData) {
        this.downstream = downstream;
        this.login = login;
        this.identityData = identityData;
        this.rawExtraData = rawExtraData;
        this.clientData = clientData;

        this.chainData = LoginPacketUtils.createChainDataJwt(this.keyPair, this.rawExtraData);

        connect();
    }

    @SneakyThrows
    private ChannelFuture connect() {
        return Ouranos.getOuranos().prepareUpstreamBootstrap()
                .handler(new BedrockChannelInitializer<ProxyClientSession>() {
                    @Override
                    protected ProxyClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new ProxyClientSession(peer, subClientId);
                    }

                    @Override
                    protected void initSession(ProxyClientSession upstream) {
                        //TODO: auto determine which codec should be used.
                        upstream.setCodec(Ouranos.REMOTE_CODEC);
                        upstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);

                        val session = new OuranosProxySession(keyPair, upstream, downstream);

                        val downstreamProtocolId = downstream.getCodec().getProtocolVersion();
                        val upstreamProtocolId = upstream.getCodec().getProtocolVersion();

                        log.info("{}({}) connected to the remote server, {}=>{}", downstream.getPeer().getSocketAddress(), downstreamProtocolId, downstreamProtocolId, upstreamProtocolId);

                        session.setDownstreamHandler(new DownstreamRewriteHandler(session));
                        session.setUpstreamHandler(new UpstreamInitialHandler(session, assembleLoginPacket(session)));
                    }
                })
                .connect(Ouranos.getOuranos().getConfig().getRemote());
    }

    @SneakyThrows
    private LoginPacket assembleLoginPacket(OuranosProxySession session) {
        var newClientData = LoginPacketUtils.writeClientData(this.keyPair, session, this.identityData, this.clientData, Ouranos.getOuranos().getConfig().login_extra);
        var newLogin = new LoginPacket();
        if (System.getenv("USE_XBOX") != null) {
            var lo = XboxLogin.getAccessToken(System.getenv("XBOX_ACCOUNT"), System.getenv("XBOX_PASSWORD"));
            var x = new Xbox(lo);
            List<String> chain = new Auth().getOnlineChainData(x, this.keyPair);
            newLogin.getChain().addAll(chain);
            var chainD = EncryptionUtils.validateChain(chain);

            var claims = chainD.identityClaims();
            var extraData = claims.extraData;
            session.identity = new AuthData(extraData.displayName,
                    extraData.identity, extraData.xuid);
        } else {
            newLogin.getChain().add(this.chainData);
            var chainD = EncryptionUtils.validateChain(List.of(this.chainData));
            var claims = chainD.identityClaims();
            var extraData = claims.extraData;
            session.identity = new AuthData(extraData.displayName,
                    extraData.identity, extraData.xuid);
        }
        newLogin.setExtra(newClientData);
        newLogin.setProtocolVersion(session.upstream.getCodec().getProtocolVersion());
        return newLogin;
    }
}
