package com.github.blackjack200.ouranos;

import cn.hutool.core.io.FileUtil;
import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.session.CustomPeer;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.ProxyServerSession;
import com.github.blackjack200.ouranos.network.session.handler.downstream.DownstreamInitialHandler;
import com.github.blackjack200.ouranos.utils.PingUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class Ouranos {
    public static final Path SERVER_CONFIG_FILE = Path.of("config.json");

    @Getter
    private static Ouranos ouranos;
    @Getter
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Getter
    private final EventLoopGroup bossGroup;
    @Getter
    private final EventLoopGroup workerGroup;

    public static volatile BedrockCodec REMOTE_CODEC = ProtocolInfo.getDefaultPacketCodec();

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(true);
    @Getter
    private final ServerConfig config;


    @SneakyThrows
    private Ouranos() {
        ouranos = this;
        if (!FileUtil.exist(SERVER_CONFIG_FILE.toFile()) || SERVER_CONFIG_FILE.toFile().getTotalSpace() == 0) {
            Files.writeString(SERVER_CONFIG_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(new ServerConfig()));
        }
        this.config = new Gson().fromJson(new FileReader(SERVER_CONFIG_FILE.toFile()), TypeToken.get(ServerConfig.class));
        if (this.config.debug) {
            Configurator.setRootLevel(Level.DEBUG);
        } else {
            Configurator.setRootLevel(Level.INFO);
        }

        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    @SneakyThrows
    private void start() {
        val start = System.currentTimeMillis();
        if (System.getenv().containsKey("DEBUG")) {
            ;
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
            log.warn("Resource leak detector has enabled");
        }
        log.info("Connecting to {}...", this.config.getRemote());
        var pong = PingUtils.ping(this.config.getRemote(), 1, TimeUnit.SECONDS).get();
        if (pong == null) {
            log.fatal("Failed to connect to {}...", this.config.getRemote());
            this.shutdown(true);
            return;
        }

        REMOTE_CODEC = ProtocolInfo.getPacketCodec(pong.protocolVersion());

        if (REMOTE_CODEC == null) {
            log.fatal("Unsupported minecraft version {}({})", pong.version(), pong.protocolVersion());
            this.shutdown(true);
            return;
        }

        BlockStateDictionary.getInstance(REMOTE_CODEC.getProtocolVersion());
        ItemTypeDictionary.getInstance(REMOTE_CODEC.getProtocolVersion());
        GlobalItemDataHandlers.getUpgrader();

        log.info("Using codec: {} {}", REMOTE_CODEC.getProtocolVersion(), REMOTE_CODEC.getMinecraftVersion());

        var boostrap = new ServerBootstrap().channelFactory(RakChannelFactory.server(NioDatagramChannel.class)).option(RakChannelOption.RAK_PACKET_LIMIT, 200).option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, ProtocolInfo.getPacketCodecs().stream().mapToInt(BedrockCodec::getRaknetProtocolVersion).distinct().toArray()).group(this.bossGroup, this.workerGroup).childHandler(new BedrockChannelInitializer<ProxyServerSession>() {
            @Override
            protected ProxyServerSession createSession0(BedrockPeer peer, int subClientId) {
                return new ProxyServerSession((CustomPeer) peer, subClientId);
            }

            @Override
            protected CustomPeer createPeer(Channel channel) {
                return new CustomPeer(channel, this::createSession);
            }

            @Override
            protected void initSession(ProxyServerSession session) {
                if (OuranosProxySession.ouranosPlayers.size() >= Ouranos.this.config.maximum_player) {
                    session.disconnect("Ouranos proxy is full.");
                    return;
                }
                log.info("{} connected", session.getPeer().getSocketAddress());
                session.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.UNLIMITED);
                session.setPacketHandler(new DownstreamInitialHandler(session));
            }
        });

        var bindv4 = this.config.getBindv4();
        var bindv6 = this.config.getBindv6();
        var channels = new ArrayList<Channel>(2);
        channels.add(boostrap.bind(bindv4).awaitUninterruptibly().channel());
        log.info("Ouranos started on {}", bindv4);

        if (this.config.server_ipv6_enabled) {
            channels.add(boostrap.bind(bindv6).awaitUninterruptibly().channel());
            log.info("Ouranos started on {}", bindv6);
        }

        log.info("Supported versions: {}", String.join(", ", ProtocolInfo.getPacketCodecs().stream().sorted(Comparator.comparingInt(BedrockCodec::getProtocolVersion)).map(BedrockCodec::getMinecraftVersion).distinct().toList()));
        log.info("Packet buffer: {}", this.config.packet_buffering);

        val done = System.currentTimeMillis();
        val time = done - start;
        log.info("Server started in {} ms", time);

        val motdLoading = new AtomicBoolean(false);
        scheduler.scheduleAtFixedRate(() -> {
            if (!this.running.get() || !motdLoading.compareAndSet(false, true)) {
                return;
            }
            PingUtils.ping((p) -> {
                if (p == null) {
                    p = this.config.getFallbackPong();
                }
                var buf = p.ipv4Port(this.config.server_port_v4).ipv6Port(this.config.server_port_v6).toByteBuf();
                for (val channel : channels) {
                    channel.config().setOption(RakChannelOption.RAK_ADVERTISEMENT, buf);
                }
                motdLoading.set(false);
            }, this.config.getRemote(), 5, TimeUnit.SECONDS);
        }, 0, 1, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Ouranos.this.shutdown(true);
        }));

        Scanner scanner = new Scanner(System.in);

        while (this.running.get() && !this.bossGroup.isShutdown() && !this.workerGroup.isShutdown()) {
            if (!scanner.hasNextLine()) {
                Thread.sleep(50);
                continue;
            }
            String input = scanner.nextLine().toLowerCase().trim();
            if (input.isEmpty()) {
                continue;
            }
            switch (input.toLowerCase()) {
                case "stop":
                case "exit":
                    log.info("Stopping the server...");
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
            Thread.sleep(50);
        }
        scheduler.shutdown();
        log.info("Ouranos shutdown gracefully.");
    }

    @SneakyThrows
    public void shutdown(boolean force) {
        if (!this.running.get()) {
            return;
        }
        val iter = OuranosProxySession.ouranosPlayers.iterator();
        while (iter.hasNext()) {
            val player = iter.next();
            iter.remove();
            player.disconnect("Ouranos closed.");
        }
        if (force) {
            this.bossGroup.shutdownGracefully(2, 2, TimeUnit.SECONDS);
            this.workerGroup.shutdownGracefully(2, 2, TimeUnit.SECONDS);
        } else {
            OuranosProxySession.ouranosPlayers.clear();
            this.bossGroup.shutdownGracefully().get(10, TimeUnit.SECONDS);
            this.workerGroup.shutdownGracefully().get(10, TimeUnit.SECONDS);
        }
        this.running.set(false);
    }

    public Bootstrap prepareUpstreamBootstrap() {
        return new Bootstrap().group(this.bossGroup)
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, REMOTE_CODEC.getRaknetProtocolVersion())
                .option(RakChannelOption.RAK_COMPATIBILITY_MODE, true)
                .option(RakChannelOption.RAK_AUTO_FLUSH, true)
                .option(RakChannelOption.RAK_FLUSH_INTERVAL, 10);
    }

    public static void main(String[] args) {
        val main = new Ouranos();
        main.start();
    }
}
