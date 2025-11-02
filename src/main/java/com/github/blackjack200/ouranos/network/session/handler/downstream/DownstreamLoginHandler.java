package com.github.blackjack200.ouranos.network.session.handler.downstream;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.TcpChannelInitializer;
import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.session.*;
import com.github.blackjack200.ouranos.network.session.handler.upstream.UpstreamInitialHandler;
import com.github.blackjack200.ouranos.utils.EncUtils;
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
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.jose4j.json.internal.json_simple.JSONObject;

import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class DownstreamLoginHandler implements BedrockPacketHandler {
    private final ProxyServerSession downstream;
    private final KeyPair keyPair;
    private final AuthData identityData;
    private final JSONObject clientData;
    private final AuthPayload authPayload;
    private String accessToken;

    public DownstreamLoginHandler(KeyPair keyPair, ProxyServerSession downstream, AuthData identityData, AuthPayload payload, JSONObject clientData) {
        this.keyPair = keyPair;
        this.downstream = downstream;
        this.identityData = identityData;
        this.clientData = clientData;
        this.authPayload = payload;
        if (System.getenv("USE_XBOX") != null) {
            try {
                accessToken = XboxLogin.getAccessToken(System.getenv("XBOX_ACCOUNT"), System.getenv("XBOX_PASSWORD"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (Ouranos.getOuranos().getConfig().proxy_protocol) {
            connectProxy();
        } else {
            connect();
        }
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
                        initSession0(upstream);
                    }
                }).connect(Ouranos.getOuranos().getConfig().getRemote());
    }

    @SneakyThrows
    private ChannelFuture connectProxy() {
        return Ouranos.getOuranos().prepareUpstreamBootstrap()
                .handler(new TcpChannelInitializer<ProxyClientSession>() {
                    @Override
                    protected ProxyClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new ProxyClientSession((CustomPeer) peer, subClientId);
                    }

                    @Override
                    protected BedrockPeer createPeer(Channel channel) {
                        return new CustomPeer(channel, this::createSession) {
                            @Override
                            public int getRakVersion() {
                                return Ouranos.REMOTE_CODEC.getRaknetProtocolVersion();
                            }
                        };
                    }

                    @Override
                    protected void initSession(ProxyClientSession upstream) {
                        initSession0(upstream);
                    }

                    @Override
                    protected int getRakVersion() {
                        return Ouranos.REMOTE_CODEC.getRaknetProtocolVersion();
                    }
                }).connect(Ouranos.getOuranos().getConfig().getRemote());
    }

    private void initSession0(ProxyClientSession upstream) {
        //TODO: auto determine which codec should be used.
        upstream.setCodec(Ouranos.REMOTE_CODEC);
        upstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);

        val session = new OuranosProxySession(keyPair, upstream, downstream);
        session.movement.inputMode = InputMode.from(Integer.parseInt(clientData.get("CurrentInputMode").toString()));

        val downstreamProtocolId = downstream.getCodec().getProtocolVersion();
        val upstreamProtocolId = upstream.getCodec().getProtocolVersion();

        log.info("{}[{}({})] connected to the remote server through {}, {}=>{}", DownstreamLoginHandler.this.identityData.displayName(), downstream.getPeer().getSocketAddress(), downstreamProtocolId, upstream.getPeer().getChannel().getClass().getSimpleName(), downstreamProtocolId, upstreamProtocolId);

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

    @SneakyThrows
    private LoginPacket assembleLoginPacket(OuranosProxySession session) {
        var newClientData = LoginPacketUtils.writeClientData(this.keyPair, session, this.identityData, this.clientData, Ouranos.getOuranos().getConfig().login_extra);
        LoginPacketUtils.validateClientData(this.clientData);
        var newLogin = new LoginPacket();
        if (System.getenv("USE_XBOX") != null) {
            var x = new Xbox(accessToken);
            List<String> chain = new Auth().getOnlineChainData(x, this.keyPair);
            newLogin.setAuthPayload(new CertificateChainPayload(chain, AuthType.FULL));
            var chainD = EncUtils.validateChain(chain);

            var claims = chainD.identityClaims();
            var extraData = claims.extraData;
            session.identity = new AuthData(extraData.displayName, extraData.xuid);
        } else {
            newLogin.setAuthPayload(this.authPayload);
            session.identity = this.identityData;
        }
        newLogin.setClientJwt(newClientData);
        newLogin.setProtocolVersion(session.upstream.getCodec().getProtocolVersion());
        return newLogin;
    }
}
