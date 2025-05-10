package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.session.*;
import com.github.blackjack200.ouranos.network.session.handler.upstream.UpstreamInitialHandler;
import com.github.blackjack200.ouranos.utils.LoginPacketUtils;
import com.github.blackjack200.ouranos.utils.auth.Auth;
import com.github.blackjack200.ouranos.utils.auth.Xbox;
import com.github.blackjack200.ouranos.utils.auth.XboxLogin;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.jose4j.json.internal.json_simple.JSONObject;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class DownstreamLoginHandler implements BedrockPacketHandler {
    private final ProxyServerSession downstream;
    private final KeyPair keyPair = EncryptionUtils.createKeyPair();
    private final AuthData identityData;
    private final Map<String, Object> rawExtraData;
    private final JSONObject clientData;
    private final String chainData;
    private String accessToken;

    public DownstreamLoginHandler(ProxyServerSession downstream, AuthData identityData, Map<String, Object> rawExtraData, JSONObject clientData) {
        this.downstream = downstream;
        this.identityData = identityData;
        this.rawExtraData = rawExtraData;
        this.clientData = clientData;
        this.chainData = LoginPacketUtils.createChainDataJwt(this.keyPair, this.rawExtraData);
        if (System.getenv("USE_XBOX") != null) {
            try {
                accessToken = XboxLogin.getAccessToken(System.getenv("XBOX_ACCOUNT"), System.getenv("XBOX_PASSWORD"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        connect();
    }

    @SneakyThrows
    private ChannelFuture connect() {
        return Ouranos.getOuranos().prepareUpstreamBootstrap()
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, Ouranos.REMOTE_CODEC.getRaknetProtocolVersion())
                .handler(new BedrockChannelInitializer<ProxyClientSession>() {
                    @Override
                    protected ProxyClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new ProxyClientSession((CustomPeer) peer, subClientId);
                    }

                    @Override
                    protected BedrockPeer createPeer(Channel channel) {
                        return new CustomPeer(channel, this::createSession);
                    }

                    @Override
                    protected void initSession(ProxyClientSession upstream) {
                        //TODO: auto determine which codec should be used.
                        upstream.setCodec(Ouranos.REMOTE_CODEC);
                        upstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);

                        val session = new OuranosProxySession(keyPair, upstream, downstream);
                        session.movement.inputMode = InputMode.from(Integer.parseInt(clientData.get("CurrentInputMode").toString()));

                        val downstreamProtocolId = downstream.getCodec().getProtocolVersion();
                        val upstreamProtocolId = upstream.getCodec().getProtocolVersion();

                        log.info("{}[{}({})] connected to the remote server, {}=>{}", DownstreamLoginHandler.this.identityData.displayName(), downstream.getPeer().getSocketAddress(), downstreamProtocolId, downstreamProtocolId, upstreamProtocolId);

                        session.setDownstreamHandler(new DownstreamRewriteHandler(session));

                        CompletableFuture.supplyAsync(() -> {
                            BlockStateDictionary.getInstance(downstreamProtocolId);
                            ItemTypeDictionary.getInstance(downstreamProtocolId);
                            BlockStateDictionary.getInstance(upstreamProtocolId);
                            ItemTypeDictionary.getInstance(upstreamProtocolId);
                            return new UpstreamInitialHandler(session, assembleLoginPacket(session));
                        }, Ouranos.getOuranos().getScheduler()).thenAcceptAsync(handler -> {
                            if (!session.isAlive()) {
                                return;
                            }
                            session.upstream.getPeer().getChannel().eventLoop().execute(() -> session.setUpstreamHandler(handler));
                        }).exceptionally(ex -> {
                            if (!session.isAlive()) {
                                return null;
                            }
                            log.error("Error while initializing data mapping/assemble login packet", ex);
                            session.disconnect("Error while initializing data mapping/assemble login packet: " + ex.getMessage());
                            return null;
                        });
                    }
                })
                .connect(Ouranos.getOuranos().getConfig().getRemote());
    }

    @SneakyThrows
    private LoginPacket assembleLoginPacket(OuranosProxySession session) {
        var newClientData = LoginPacketUtils.writeClientData(this.keyPair, session, this.identityData, this.clientData, Ouranos.getOuranos().getConfig().login_extra);
        LoginPacketUtils.validateClientData(this.clientData);
        var newLogin = new LoginPacket();
        if (System.getenv("USE_XBOX") != null) {
            var x = new Xbox(accessToken);
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
