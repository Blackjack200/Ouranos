package com.blackjack200.ouranos;

import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import com.blackjack200.ouranos.network.session.AuthData;
import com.blackjack200.ouranos.network.session.DownstreamSession;
import com.blackjack200.ouranos.network.session.UpstreamSession;
import com.blackjack200.ouranos.network.translate.Translate;
import com.blackjack200.ouranos.utils.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.nbt.*;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Log4j2
public class Ouranos {
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final BedrockCodec REMOTE_CODEC = Bedrock_v685.CODEC;
    private NioEventLoopGroup group;

    private Ouranos() {
        this.config = new ServerConfig(new YamlConfig(new File("config.yml")));
    }

    private void start() {
        RuntimeBlockMapping.getInstance();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);

        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", 19132);
        val pong = this.config.getPong();

        pong.ipv4Port(this.config.getBind().getPort())
                .ipv6Port(this.config.getBind().getPort());

        var protocol = ProtocolInfo.getPacketCodecs().stream().mapToInt(BedrockCodec::getRaknetProtocolVersion).distinct().toArray();
        group = new NioEventLoopGroup();

        new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
                .group(group)
                .childHandler(new BedrockChannelInitializer<DownstreamSession>() {
                    @Override
                    protected DownstreamSession createSession0(BedrockPeer peer, int subClientId) {
                        return new DownstreamSession(peer, subClientId);
                    }

                    @Override
                    protected void initSession(DownstreamSession session) {
                        log.info("{} connected", session.getPeer().getSocketAddress());
                        session.setPacketHandler(new BedrockPacketHandler() {
                            @Override
                            public void onDisconnect(String reason) {
                                log.info("{} disconnected due to {}", session.getPeer().getSocketAddress(), reason);
                            }

                            @Override
                            public PacketSignal handle(LoginPacket packet) {
                                val codec = session.getCodec();
                                log.info("{} log-in using minecraft {} {}", session.getPeer().getSocketAddress(), codec.getMinecraftVersion(), codec.getProtocolVersion());
                                handlePlayerLogin(session, packet);
                                return PacketSignal.HANDLED;
                            }

                            @Override
                            public PacketSignal handle(RequestNetworkSettingsPacket packet) {
                                session.setCodec(ProtocolInfo.getPacketCodec(packet.getProtocolVersion()));
                                val codec = session.getCodec();
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
                .syncUninterruptibly();

        log.info("Remote codec: {} {}", REMOTE_CODEC.getProtocolVersion(), REMOTE_CODEC.getMinecraftVersion());

        log.info("Ouranos started at {}", this.config.getBind());
        this.running.set(true);
        while (this.running.get()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("Ouranos shutdown gracefully");
    }

    public Object loadGzipNBT(String dataName) {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(dataName);
             NBTInputStream nbtInputStream = NbtUtils.createGZIPReader(inputStream)) {
            return nbtInputStream.readTag();
        } catch (IOException e) {
            return null;
        }
    }

    private void handlePlayerLogin(DownstreamSession client, LoginPacket loginPacket) {
        newClient(this.config.getRemote(), (upstream) -> {
            client.upstream = upstream;
            upstream.setCodec(REMOTE_CODEC);

            log.info("{} connected to the remote server {}=>{}", client.getPeer().getSocketAddress(), client.getCodec().getProtocolVersion(), upstream.getCodec().getProtocolVersion());

            client.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);
            upstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);

            val packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(upstream.getCodec().getProtocolVersion());
            upstream.sendPacketImmediately(packet);

            client.setPacketHandler(new BedrockPacketHandler() {
                @Override
                public PacketSignal handlePacket(BedrockPacket packet) {
                    if (!(packet instanceof PlayerAuthInputPacket)) {
                        log.info("-> {}", packet.getPacketType());
                    }
                    ReferenceCountUtil.retain(packet);
                    upstream.sendPacket(packet);
                    return PacketSignal.HANDLED;
                }

                @Override
                public void onDisconnect(String reason) {
                    if (upstream.isConnected()) {
                        upstream.disconnect(reason);
                        upstream.getPeer().getChannel().close();
                    }
                }

                @Override
                public PacketSignal handle(DisconnectPacket packet) {
                    if (upstream.isConnected()) {
                        upstream.disconnect(packet.getKickMessage());
                        upstream.getPeer().getChannel().close();
                    }
                    return PacketSignal.HANDLED;
                }
            });

            upstream.setPacketHandler(new BedrockPacketHandler() {
                @Override
                public void onDisconnect(String reason) {
                    if (client.isConnected()) {
                        client.disconnect(reason);
                        client.getPeer().getChannel().close();
                    }
                }

                @Override
                public PacketSignal handlePacket(BedrockPacket packet) {
                    log.info("<- {}", packet.getPacketType());
                    if (packet instanceof DisconnectPacket pk) {
                        if (client.isConnected()) {
                            client.disconnect(pk.getKickMessage());
                        }
                        client.getPeer().getChannel().close();
                        return PacketSignal.HANDLED;
                    }
                    if (packet instanceof NetworkSettingsPacket pk) {
                        upstream.setCompression(pk.getCompressionAlgorithm());
                        try {
                            var login = makeNewLoginPacket(loginPacket, client);
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
                            SecretKey key = EncryptionUtils.getSecretKey(EncryptionUtils.createKeyPair().getPrivate(), serverKey,
                                    Base64.getDecoder().decode(JsonUtils.childAsType(saltJwt, "salt", String.class)));
                            upstream.enableEncryption(key);
                        } catch (JoseException | NoSuchAlgorithmException | InvalidKeySpecException |
                                 InvalidKeyException e) {
                            throw new RuntimeException(e);
                        }

                        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();

                        upstream.sendPacketImmediately(clientToServerHandshake);
                        return PacketSignal.HANDLED;
                    }
                    if (packet instanceof StartGamePacket pk) {
                        var itemRegistry = SimpleDefinitionRegistry.<ItemDefinition>builder()
                                .addAll(pk.getItemDefinitions())
                                .add(new SimpleItemDefinition("minecraft:empty", 0, false))
                                .build();
                        //var blockRegistry = new NbtBlockDefinitionRegistry(pk.getBlockPalette());
                        // Load block palette, if it exists

                        upstream.getPeer().getCodecHelper().setItemDefinitions(itemRegistry);
                        client.getPeer().getCodecHelper().setItemDefinitions(itemRegistry);

                        Object object = Ouranos.this.loadGzipNBT("block_palette_729.nbt");

                        DefinitionRegistry<BlockDefinition> blockDefinitions;
                        if (object instanceof NbtMap map) {
                            blockDefinitions = new NbtBlockDefinitionRegistry(map.getList("blocks", NbtType.COMPOUND));
                            pk.setBlockPalette(new NbtList<>(NbtType.COMPOUND, map.getList("blocks", NbtType.COMPOUND)));
                        } else {
                            blockDefinitions = new UnknownBlockDefinitionRegistry();
                            log.warn("Failed to load block palette. Blocks will appear as runtime IDs in packet traces and creative_content.json!");
                        }

                        upstream.getPeer().getCodecHelper().setBlockDefinitions(blockDefinitions);
                        client.getPeer().getCodecHelper().setBlockDefinitions(blockDefinitions);
                    }

                    if (packet instanceof LevelChunkPacket chunk) {
                        ByteBuf from = chunk.getData();
                        ByteBuf to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());

                        boolean success = Ouranos.rewriteChunkData(client, from, to, chunk.getSubChunksLength());
                        if (success) {
                            chunk.setData(to);
                        }
                    }

                    ReferenceCountUtil.retain(packet);
                    var originalProtocolId = client.upstream.getCodec().getProtocolVersion();
                    var targetProtocolId = client.getCodec().getProtocolVersion();
                    client.sendPacket(Translate.translate(originalProtocolId,targetProtocolId,packet));
                   // client.sendPacket(packet);
                    return PacketSignal.HANDLED;
                }
            });
        });
    }

    private static LoginPacket makeNewLoginPacket(LoginPacket loginPacket, DownstreamSession client) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException {
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
        var chainData = HandshakeUtils.createExtraData(client.keyPair, extraData);

        var authData = ForgeryUtils.forgeAuthData(client.keyPair, extraData);
        var skinDataString = ForgeryUtils.forgeSkinData(client.keyPair, skinData);

        LoginPacket login = new LoginPacket();
        login.getChain().add(chainData.serialize());
        login.setExtra(skinDataString);
        login.setProtocolVersion(client.upstream.getCodec().getProtocolVersion());
        return login;
    }

    public void newClient(InetSocketAddress socketAddress, Consumer<UpstreamSession> sessionConsumer) {
        new Bootstrap()
                .group(group)
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, REMOTE_CODEC.getRaknetProtocolVersion())
                .handler(new BedrockChannelInitializer<UpstreamSession>() {

                    @Override
                    protected UpstreamSession createSession0(BedrockPeer peer, int subClientId) {
                        return new UpstreamSession(peer, subClientId);
                    }

                    @Override
                    protected void initSession(UpstreamSession session) {
                        sessionConsumer.accept(session);
                    }
                })
                .connect(socketAddress)
                .awaitUninterruptibly();
    }

    public void shutdown() {
        this.running.set(false);
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("log4j.skipJansi", "false");
        val main = new Ouranos();
        main.start();
    }

    private static boolean rewriteChunkData(DownstreamSession sess, ByteBuf from, ByteBuf to, int sections) {
        for (int section = 0; section < sections; section++) {
            int chunkVersion = from.readUnsignedByte();
            to.writeByte(chunkVersion);

            switch (chunkVersion) {
                // Legacy block ids, no remap needed
                // MiNet uses this format
                case 0, 4, 139 -> {
                    to.writeBytes(from);
                    return true;
                }
                case 8 -> { // New form chunk, baked-in palette
                    int storageCount = from.readUnsignedByte();
                    to.writeByte(storageCount);
                    for (int storage = 0; storage < storageCount; storage++) {
                        int flags = from.readUnsignedByte();
                        int bitsPerBlock = flags >> 1; // isRuntime = (flags & 0x1) != 0
                        int blocksPerWord = Integer.SIZE / bitsPerBlock;
                        int nWords = ((16 * 16 * 16) + blocksPerWord - 1) / blocksPerWord;

                        to.writeByte(flags);
                        to.writeBytes(from, nWords * Integer.BYTES);

                        int nPaletteEntries = VarInts.readInt(from);
                        VarInts.writeInt(to, nPaletteEntries);

                        for (int i = 0; i < nPaletteEntries; i++) {
                            int runtimeId = VarInts.readInt(from);
                            VarInts.writeInt(to, Ouranos.translateBlockRuntimeId(sess, runtimeId));
                        }
                    }
                }
                default -> { // Unsupported
                    log.warn("PEBlockRewrite: Unknown subchunk format " + chunkVersion);
                    return false;
                }
            }
        }
        return true;
    }

    public static int translateBlockRuntimeId(DownstreamSession sess, int blockRuntimeId) {
        var originalProtocolId = sess.upstream.getCodec().getProtocolVersion();
        var targetProtocolId = sess.getCodec().getProtocolVersion();
        if (blockRuntimeId == 0) {
            return 0;
        }
        val internalStateId = RuntimeBlockMapping.getInstance().fromRuntimeId(originalProtocolId, blockRuntimeId);
        return RuntimeBlockMapping.getInstance().toRuntimeId(targetProtocolId, internalStateId);
    }

}
