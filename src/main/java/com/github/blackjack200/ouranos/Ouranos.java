package com.github.blackjack200.ouranos;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.session.AuthData;
import com.github.blackjack200.ouranos.network.session.OuranosPlayer;
import com.github.blackjack200.ouranos.network.session.Translate;
import com.github.blackjack200.ouranos.utils.BlockDictionaryRegistry;
import com.github.blackjack200.ouranos.utils.ItemTypeDictionaryRegistry;
import com.github.blackjack200.ouranos.utils.LoginPacketUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class Ouranos {
    public static final Path SERVER_CONFIG_FILE = Path.of("config.json");
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public static BedrockCodec REMOTE_CODEC = ProtocolInfo.getDefaultPacketCodec();
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    @SneakyThrows
    private Ouranos() {
        if (!FileUtil.exist(SERVER_CONFIG_FILE.toFile()) || SERVER_CONFIG_FILE.toFile().getTotalSpace() == 0) {
            Files.writeString(SERVER_CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(new ServerConfig()));
        }
        this.config = new Gson().fromJson(new FileReader(SERVER_CONFIG_FILE.toFile()), TypeToken.get(ServerConfig.class));
        if (this.config.debug) {
            Configurator.setRootLevel(Level.DEBUG);
        } else {
            Configurator.setRootLevel(Level.INFO);
        }
    }

    @SneakyThrows
    private void start() {
        val start = System.currentTimeMillis();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);

        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = this.bossGroup;

        var boostrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_ADVERTISEMENT, this.config.getPong().toByteBuf())
                .option(RakChannelOption.RAK_PACKET_LIMIT, 200)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, ProtocolInfo.getPacketCodecs().stream().mapToInt(BedrockCodec::getRaknetProtocolVersion).distinct().toArray())
                .group(this.bossGroup, this.workerGroup)
                .childHandler(new BedrockServerInitializer() {
                    @Override
                    protected void initSession(BedrockServerSession session) {
                        if (OuranosPlayer.ouranosPlayers.size() >= Ouranos.this.config.maximum_player) {
                            session.disconnect("Ouranos proxy is full.");
                            return;
                        }
                        log.info("{} connected", session.getPeer().getSocketAddress());
                        session.setPacketHandler(new BedrockPacketHandler() {
                            @Override
                            public void onDisconnect(String reason) {
                                log.info("{} disconnected due to {}", session.getPeer().getSocketAddress(), reason);
                            }

                            private BedrockCodec setupCodec(int protocolId) {
                                val codec = ProtocolInfo.getPacketCodec(protocolId);
                                if (codec == null) {
                                    log.error("Protocol version {} is not supported", protocolId);
                                    val status = new PlayStatusPacket();
                                    status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
                                    session.sendPacketImmediately(status);
                                    session.disconnect();
                                    return null;
                                }
                                return codec;
                            }

                            @Override
                            @SneakyThrows
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
                });

        var bindv4 = this.config.getBindv4();
        var bindv6 = this.config.getBindv6();
        boostrap.bind(bindv4).awaitUninterruptibly();
        log.info("Ouranos started on {}", bindv4);

        if (this.config.server_ipv6_enabled) {
            boostrap.bind(bindv6).awaitUninterruptibly();
            log.info("Ouranos started on {}", bindv6);
        }

        REMOTE_CODEC = this.config.getRemoteCodec();

        log.info("Using codec: {} {}", REMOTE_CODEC.getProtocolVersion(), REMOTE_CODEC.getMinecraftVersion());

        log.info("Supported versions: {}", String.join(", ", ProtocolInfo.getPacketCodecs().stream().sorted(Comparator.comparingInt(BedrockCodec::getProtocolVersion)).map(BedrockCodec::getMinecraftVersion).distinct().toList()));

        val done = System.currentTimeMillis();
        val time = done - start;
        log.info("Server started in {} ms", time);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Ouranos.this.shutdown(true);
        }));
        Scanner scanner = new Scanner(System.in);

        while (this.running.get() && !this.bossGroup.isShutdown() && !this.workerGroup.isShutdown()) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().toLowerCase().trim();
                if (input.isEmpty()) {
                    continue;
                }
                switch (input.toLowerCase()) {
                    case "stop":
                    case "exit":
                        this.shutdown(false);
                        break;
                    case "gc":
                        val runtime = Runtime.getRuntime();
                        val usedMemory = runtime.totalMemory() - runtime.freeMemory();
                        log.info("Memory used: {} MB", Math.round((usedMemory / 1024d / 1024d)));
                        System.gc();
                        val freedMemory = usedMemory - (runtime.totalMemory() - runtime.freeMemory());
                        log.info("Memory freed: {} MB", Math.round((freedMemory / 1024d / 1024d)));
                        break;
                    default:
                        log.error("Unknown command: {}", input);
                }
            }
        }
        log.info("Ouranos shutdown gracefully.");
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

                        log.info("{}({}) connected to the remote server using protocol {}", downstream.getPeer().getSocketAddress(), downstreamProtocolId, upstreamProtocolId);

                        val packet = new RequestNetworkSettingsPacket();
                        packet.setProtocolVersion(upstreamProtocolId);
                        upstream.sendPacketImmediately(packet);

                        player.setDownstreamHandler(new BedrockPacketHandler() {
                            @Override
                            public PacketSignal handlePacket(BedrockPacket packet) {

                                ReferenceCountUtil.retain(packet);
                                if (upstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                                    var packets = Translate.translate(player.getDownstreamProtocolId(), player.getUpstreamProtocolId(), player, packet);
                                    for (var pk : packets) {
                                        if (Ouranos.this.config.debug && !(pk instanceof PlayerAuthInputPacket)) {
                                            log.debug("C->S {}", pk);
                                        }
                                        upstream.sendPacket(pk);
                                    }
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
                                        if (login == null) {
                                            return PacketSignal.HANDLED;
                                        }
                                        upstream.sendPacketImmediately(login);
                                    } catch (JoseException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return PacketSignal.HANDLED;
                                }
                                if (packet instanceof ServerToClientHandshakePacket pk) {
                                    upstream.getPeer().getChannel().eventLoop().execute(() -> {
                                        try {
                                            var jws = new JsonWebSignature();
                                            jws.setCompactSerialization(pk.getJwt());
                                            var saltJwt = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));
                                            var x5u = jws.getHeader(HeaderParameterNames.X509_URL);
                                            var serverKey = EncryptionUtils.parseKey(x5u);
                                            var key = EncryptionUtils.getSecretKey(player.getKeyPair().getPrivate(), serverKey,
                                                    Base64.getDecoder().decode(JsonUtils.childAsType(saltJwt, "salt", String.class)));
                                            upstream.enableEncryption(key);
                                        } catch (JoseException | NoSuchAlgorithmException | InvalidKeySpecException |
                                                 InvalidKeyException e) {
                                            throw new RuntimeException(e);
                                        }
                                        val handshake = new ClientToServerHandshakePacket();
                                        upstream.sendPacketImmediately(handshake);
                                    });
                                    return PacketSignal.HANDLED;
                                }
                                if (packet instanceof StartGamePacket pk) {
                                    upstream.getPeer().getCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(upstreamProtocolId));
                                    downstream.getPeer().getCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(downstreamProtocolId));

                                    List<ItemDefinition> def = ItemTypeDictionary.getInstance(downstreamProtocolId).getEntries().entrySet().stream().<ItemDefinition>map((e) -> new SimpleItemDefinition(e.getKey(), e.getValue().runtime_id(), e.getValue().component_based())).toList();
                                    pk.setItemDefinitions(def);

                                    upstream.getPeer().getCodecHelper().setBlockDefinitions(new BlockDictionaryRegistry(upstreamProtocolId));
                                    downstream.getPeer().getCodecHelper().setBlockDefinitions(new BlockDictionaryRegistry(downstreamProtocolId));

                                    player.blockNetworkIdAreHashes = pk.isBlockNetworkIdsHashed();
                                    pk.setServerEngine("Ouranos"); //for telemetry

                                    downstream.sendPacketImmediately(pk);
                                    return PacketSignal.HANDLED;
                                }
                                ReferenceCountUtil.retain(packet);
                                if (downstream.getCodec().getPacketDefinition(packet.getClass()) != null) {
                                    var packets = Translate.translate(upstreamProtocolId, downstreamProtocolId, player, packet);
                                    for (var pk : packets) {
                                        if (Ouranos.this.config.debug && !(pk instanceof NetworkChunkPublisherUpdatePacket) && !(pk instanceof LevelChunkPacket) && !(pk instanceof CraftingDataPacket) && !(pk instanceof AvailableEntityIdentifiersPacket) && !(pk instanceof BiomeDefinitionListPacket)) {
                                            log.debug("S->C {}", pk);
                                        }
                                        downstream.sendPacket(pk);
                                    }
                                }
                                return PacketSignal.HANDLED;
                            }
                        });
                    }
                })
                .connect(this.config.getRemote());
    }

    private LoginPacket makeNewLoginPacket(LoginPacket loginPacket, OuranosPlayer player) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException {
        var chain = EncryptionUtils.validateChain(loginPacket.getChain());

        var claims = chain.identityClaims();
        var extraData = claims.extraData;
        var identityData = new AuthData(extraData.displayName,
                extraData.identity, extraData.xuid);

        var rawExtraData = Convert.toMap(String.class, Object.class, chain.rawIdentityClaims().get("extraData"));

        if (identityData.xuid().isEmpty() && this.config.online_mode) {
            player.disconnect("You need to authenticate to Xbox Live.");
            return null;
        }

        var jws = new JsonWebSignature();
        jws.setKey(claims.parsedIdentityPublicKey());
        jws.setCompactSerialization(loginPacket.getExtra());
        jws.setPayloadCharEncoding(String.valueOf(StandardCharsets.UTF_8));
        jws.verifySignature();

        var clientData = new JSONObject(JsonUtil.parseJson(jws.getPayload()));
        var chainData = LoginPacketUtils.createChainDataJwt(player.getKeyPair(), rawExtraData);
        var skinDataString = LoginPacketUtils.writeClientData(player.getKeyPair(), player, identityData, clientData, this.config.login_extra);

        var newLogin = new LoginPacket();
        newLogin.getChain().add(chainData);
        newLogin.setExtra(skinDataString);
        newLogin.setProtocolVersion(player.getUpstreamProtocolId());
        return newLogin;
    }

    @SneakyThrows
    public void shutdown(boolean force) {
        if (!this.running.get()) {
            return;
        }
        log.info("Stopping the server...");
        val iter = OuranosPlayer.ouranosPlayers.iterator();
        while (iter.hasNext()) {
            val player = iter.next();
            iter.remove();
            player.disconnect("Ouranos closed.");
        }
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
