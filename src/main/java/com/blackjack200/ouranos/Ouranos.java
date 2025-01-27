package com.blackjack200.ouranos;

import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import com.blackjack200.ouranos.network.session.AuthData;
import com.blackjack200.ouranos.network.session.OuranosPlayer;
import com.blackjack200.ouranos.network.translate.Translate;
import com.blackjack200.ouranos.utils.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import javax.crypto.SecretKey;
import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class Ouranos {
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public static final BedrockCodec REMOTE_CODEC = Bedrock_v766.CODEC;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    private Ouranos() {
        this.config = new ServerConfig(new YamlConfig(new File("config.yml")));
    }

    @SneakyThrows
    private void start() {
        (new Thread(RuntimeBlockMapping::getInstance)).start();

        // RuntimeBlockMapping.getInstance();
        //CreativeInventory.getInstance();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);

        val bindAddress = this.config.getBind();
        val pong = this.config.getPong();

        pong.ipv4Port(bindAddress.getPort())
                .ipv6Port(bindAddress.getPort());

        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();

        new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
                .option(RakChannelOption.RAK_PACKET_LIMIT, 200)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, ProtocolInfo.getPacketCodecs().stream().mapToInt(BedrockCodec::getRaknetProtocolVersion).distinct().toArray())
                .group(this.bossGroup, this.workerGroup)
                .childHandler(new BedrockServerInitializer() {
                    @Override
                    protected void initSession(BedrockServerSession session) {
                        log.info("{} connected", session.getPeer().getSocketAddress());
                        session.setPacketHandler(new BedrockPacketHandler() {
                            @Override
                            public void onDisconnect(String reason) {
                                log.info("{} disconnected due to {}", session.getPeer().getSocketAddress(), reason);
                            }

                            private BedrockCodec setupCodec(int protocolId) {
                                val codec = ProtocolInfo.getPacketCodec(protocolId);
                                if (codec == null) {
                                    log.error("Packet protocol version {} is not supported", protocolId);
                                    val status = new PlayStatusPacket();
                                    status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
                                    session.sendPacketImmediately(status);
                                    session.disconnect();
                                    return null;
                                }
                                return codec;
                            }

                            @Override
                            public PacketSignal handle(LoginPacket packet) {
                                val codec = setupCodec(packet.getProtocolVersion());
                                if (codec == null) {
                                    return PacketSignal.HANDLED;
                                }
                                session.setCodec(codec);
                                log.info("{} log-in using minecraft {} {}", session.getPeer().getSocketAddress(), codec.getMinecraftVersion(), codec.getProtocolVersion());
                                Ouranos.this.onPlayerLogin(session, packet).awaitUninterruptibly();
                                return PacketSignal.HANDLED;
                            }

                            @Override
                            public PacketSignal handle(RequestNetworkSettingsPacket packet) {
                                val codec = setupCodec(packet.getProtocolVersion());
                                if (codec == null) {
                                    return PacketSignal.HANDLED;
                                }
                                session.setCodec(codec);
                                log.info("{} requesting network setting with minecraft {} {}", session.getPeer().getSocketAddress(), codec.getProtocolVersion(), codec.getMinecraftVersion());
                                val pk = new NetworkSettingsPacket();
                                pk.setCompressionThreshold(0);
                                pk.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
                                session.sendPacketImmediately(pk);
                                session.setCompression(PacketCompressionAlgorithm.ZLIB);
                                return PacketSignal.HANDLED;
                            }
                        });
                    }
                })
                .bind(bindAddress)
                .awaitUninterruptibly();

        log.info("Remote codec: {} {}", REMOTE_CODEC.getProtocolVersion(), REMOTE_CODEC.getMinecraftVersion());
        log.info("Ouranos started at {}", this.config.getBind());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Ouranos.this.shutdown(true);
        }));
        Scanner scanner = new Scanner(System.in);

        while (this.running.get() && !this.bossGroup.isShutdown()) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("stop".equalsIgnoreCase(input)) {
                    log.info("Shutting down...");
                    this.shutdown(false);
                    break;
                } else {
                    log.error("Unknown command: {}", input);
                }
            }
        }
        log.info("Ouranos shutdown gracefully");
    }

    private ChannelFuture onPlayerLogin(final BedrockServerSession downstream, final LoginPacket loginPacket) {
        return new Bootstrap()
                .group(bossGroup)
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, REMOTE_CODEC.getRaknetProtocolVersion())
                .option(RakChannelOption.RAK_COMPATIBILITY_MODE, true)
                .option(RakChannelOption.RAK_AUTO_FLUSH, true)
                .option(RakChannelOption.RAK_FLUSH_INTERVAL, 1)
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession upstream) {
                        upstream.setCodec(REMOTE_CODEC);

                        downstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);
                        upstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);

                        val player = new OuranosPlayer(upstream, downstream);

                        val downstreamProtocolId = downstream.getCodec().getProtocolVersion();
                        val upstreamProtocolId = upstream.getCodec().getProtocolVersion();

                        log.info("{} connected to the remote server {}=>{}", downstream.getPeer().getSocketAddress(), downstreamProtocolId, upstreamProtocolId);

                        val packet = new RequestNetworkSettingsPacket();
                        packet.setProtocolVersion(upstreamProtocolId);
                        upstream.sendPacketImmediately(packet);

                        player.setDownstreamHandler(new BedrockPacketHandler() {
                            @Override
                            public PacketSignal handlePacket(BedrockPacket packet) {
                                if (!(packet instanceof PlayerAuthInputPacket)) {
                                    log.info("C->S {}", packet.getPacketType());
                                }
                                ReferenceCountUtil.retain(packet);
                                if (upstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                                    upstream.sendPacket(Translate.translate(player.getDownstreamProtocolId(), player.getUpstreamProtocolId(), player, packet));
                                }
                                return PacketSignal.HANDLED;
                            }

                            @Override
                            public void onDisconnect(String reason) {
                                player.disconnect(reason);
                            }

                            @Override
                            public PacketSignal handle(DisconnectPacket packet) {
                                player.disconnect(packet.getKickMessage(), packet.isMessageSkipped());
                                return PacketSignal.HANDLED;
                            }
                        });

                        player.setUpstreamHandler(new BedrockPacketHandler() {
                            @Override
                            public void onDisconnect(String reason) {
                                player.disconnect(reason);
                            }

                            @Override
                            public PacketSignal handlePacket(BedrockPacket packet) {
                                if (packet instanceof DisconnectPacket pk) {
                                    player.disconnect(pk.getKickMessage(), pk.isMessageSkipped());
                                    return PacketSignal.HANDLED;
                                }
                                if (packet instanceof NetworkSettingsPacket pk) {
                                    upstream.setCompression(pk.getCompressionAlgorithm());
                                    try {
                                        var login = makeNewLoginPacket(loginPacket, player);
                                        upstream.sendPacketImmediately(login);
                                    } catch (JoseException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return PacketSignal.HANDLED;
                                }
                                if (packet instanceof ServerToClientHandshakePacket pk) {
                                    try {
                                        JsonWebSignature jws = new JsonWebSignature();
                                        jws.setCompactSerialization(pk.getJwt());
                                        JSONObject saltJwt = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));
                                        String x5u = jws.getHeader(HeaderParameterNames.X509_URL);
                                        ECPublicKey serverKey = EncryptionUtils.parseKey(x5u);
                                        SecretKey key = EncryptionUtils.getSecretKey(player.getKeyPair().getPrivate(), serverKey,
                                                Base64.getDecoder().decode(JsonUtils.childAsType(saltJwt, "salt", String.class)));
                                        upstream.enableEncryption(key);
                                    } catch (JoseException | NoSuchAlgorithmException | InvalidKeySpecException |
                                             InvalidKeyException e) {
                                        throw new RuntimeException(e);
                                    }
                                    val handshake = new ClientToServerHandshakePacket();
                                    upstream.sendPacketImmediately(handshake);
                                    return PacketSignal.HANDLED;
                                }
                                if (packet instanceof StartGamePacket pk) {
                                    val newRegistry = SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(pk.getItemDefinitions()).build();
                                    upstream.getPeer().getCodecHelper().setItemDefinitions(newRegistry);

                                    List<ItemDefinition> def = ItemTypeDictionary.getInstance().getEntries(upstreamProtocolId).entrySet().stream().<ItemDefinition>map((e) -> new SimpleItemDefinition(e.getKey(), e.getValue().runtime_id(), e.getValue().component_based())).toList();
                                    val oldRegistry = SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(def).build();
                                    downstream.getPeer().getCodecHelper().setItemDefinitions(oldRegistry);
                                    pk.setItemDefinitions(def);

                                    val registry = new UnknownBlockDefinitionRegistry();
                                    upstream.getPeer().getCodecHelper().setBlockDefinitions(registry);
                                    downstream.getPeer().getCodecHelper().setBlockDefinitions(registry);

                                    List<NbtMap> states = RuntimeBlockMapping.getInstance().getBedrockKnownStates(upstreamProtocolId).values().stream().toList();
                                    pk.setBlockPalette(new NbtList<>(NbtType.byClass(NbtMap.class), states));
                                    pk.setServerEngine("Ouranos");
                                    downstream.sendPacketImmediately(pk);
                                    return PacketSignal.HANDLED;
                                }
                                if (!(packet instanceof LevelChunkPacket) && !(packet instanceof CraftingDataPacket) && !(packet instanceof AvailableEntityIdentifiersPacket) && !(packet instanceof BiomeDefinitionListPacket)) {
                                    log.info("C<-S {}", packet.getPacketType());
                                }
                                ReferenceCountUtil.retain(packet);
                                if (downstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                                    downstream.sendPacket(Translate.translate(player.getUpstreamProtocolId(), downstreamProtocolId, player, packet));
                                }
                                return PacketSignal.HANDLED;
                            }
                        });
                    }
                })
                .connect(this.config.getRemote());
    }

    private static LoginPacket makeNewLoginPacket(LoginPacket loginPacket, OuranosPlayer player) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException {
        ChainValidationResult chain = null;
        chain = EncryptionUtils.validateChain(loginPacket.getChain());

        var payload = chain.rawIdentityClaims();
        if (!(payload.get("extraData") instanceof Map<?, ?>)) {
            throw new RuntimeException("AuthData was not found!");
        }

        var extraData = new JSONObject(JsonUtils.childAsType(payload, "extraData", Map.class));

        var identityData = new AuthData(chain.identityClaims().extraData.displayName,
                chain.identityClaims().extraData.identity, chain.identityClaims().extraData.xuid);

        if (!(payload.get("identityPublicKey") instanceof String)) {
            throw new RuntimeException("Identity Public Key was not found!");
        }

        ECPublicKey identityPublicKey = EncryptionUtils.parseKey(payload.get("identityPublicKey").toString());

        String clientJwt = loginPacket.getExtra();
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(identityPublicKey);
        jws.setCompactSerialization(clientJwt);
        jws.verifySignature();

        var skinData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));
        var chainData = HandshakeUtils.createExtraData(player.getKeyPair(), extraData);

        var authData = ForgeryUtils.forgeAuthData(player.getKeyPair(), extraData);
        var skinDataString = ForgeryUtils.forgeSkinData(player.getKeyPair(), skinData);

        LoginPacket login = new LoginPacket();
        login.getChain().add(chainData.serialize());
        login.setExtra(skinDataString);
        login.setProtocolVersion(player.getUpstreamProtocolId());
        return login;
    }

    @SneakyThrows
    public void shutdown(boolean force) {
        if (!this.running.get()) {
            return;
        }
        OuranosPlayer.ouranosPlayers.forEach(player -> {
            player.disconnect("Ouranos closed.");
        });
        if (force) {
            this.bossGroup.shutdownGracefully(2, 2, TimeUnit.SECONDS);
            this.workerGroup.shutdownGracefully(2, 2, TimeUnit.SECONDS);
        } else {
            OuranosPlayer.ouranosPlayers.clear();
            this.bossGroup.shutdownGracefully().get(1, TimeUnit.MINUTES);
            this.workerGroup.shutdownGracefully().get(1, TimeUnit.MINUTES);
        }
        this.running.set(false);
    }

    public static void main(String[] args) {
        val main = new Ouranos();
        main.start();
    }
}
