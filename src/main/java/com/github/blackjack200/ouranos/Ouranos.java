package com.github.blackjack200.ouranos;

import cn.hutool.core.io.FileUtil;
import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.session.CustomPeer;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.ProxyServerSession;
import com.github.blackjack200.ouranos.network.session.RakServerRateLimiterOverride;
import com.github.blackjack200.ouranos.network.session.handler.downstream.DownstreamInitialHandler;
import com.github.blackjack200.ouranos.utils.PingUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class Ouranos {
    public static final Path SERVER_CONFIG_FILE = Path.of("config.json");
    private static final int SHUTDOWN_QUIET_PERIOD_MS = 100;
    private static final int SHUTDOWN_TIMEOUT_MS = 500;

    @Getter
    private static Ouranos ouranos;
    @Getter
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Getter
    private final EventLoopGroup bossGroup;
    @Getter
    private final EventLoopGroup workerGroup;

    public static volatile BedrockCodec REMOTE_CODEC;

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

        this.bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    }

    @SneakyThrows
    private void start() {
        val start = System.currentTimeMillis();
        if (System.getenv().containsKey("DEBUG")) {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
            log.warn("Resource leak detector has enabled");
        }
        log.info("Connecting to {}...", this.config.getRemote());
        int retry = 10;
        BedrockPong pong = null;
        while (retry-- > 0) {
            pong = PingUtils.ping(this.config.getRemote(), 1, TimeUnit.SECONDS).get();
            if (pong != null) {
                break;
            }
        }
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
        log.info("Using codec: {} {}", REMOTE_CODEC.getProtocolVersion(), REMOTE_CODEC.getMinecraftVersion());

        CompletableFuture.runAsync(GlobalItemDataHandlers::getUpgrader);

        var boostrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(RakChannelOption.RAK_PACKET_LIMIT, 200)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[]{11, 10, 9})
                .group(this.bossGroup, this.workerGroup)
                .childHandler(new BedrockChannelInitializer<ProxyServerSession>() {
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

        for (var channel : channels) {
            channel.pipeline().replace(RakServerRateLimiter.NAME, RakServerRateLimiterOverride.NAME, new RakServerRateLimiterOverride((RakServerChannel) channel));
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
                if (p.subMotd().isEmpty()) {
                    p = p.subMotd("Ouranos");
                }
                var count = OuranosProxySession.ouranosPlayers.size();
                var buf = p.ipv4Port(this.config.server_port_v4).ipv6Port(this.config.server_port_v6).playerCount(count).maximumPlayerCount(this.config.maximum_player).toByteBuf();
                for (val channel : channels) {
                    ReferenceCountUtil.release(channel.config().getOption(RakChannelOption.RAK_ADVERTISEMENT));
                    channel.config().setOption(RakChannelOption.RAK_ADVERTISEMENT, buf);
                }
                motdLoading.set(false);
                ReferenceCountUtil.release(p);
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
                case "debug":
                    this.config.debug = !this.config.debug;
                    if (this.config.debug) {
                        Configurator.setRootLevel(Level.DEBUG);
                        log.info("Debug mode enabled");
                    } else {
                        Configurator.setRootLevel(Level.INFO);
                        log.info("Debug mode disabled");
                    }
                    break;
                case "gc": {
                    val runtime = Runtime.getRuntime();
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    log.info("Memory used: {} MB", Math.round((usedMemory / 1024d / 1024d)));
                    System.gc();
                    val freedMemory = usedMemory - (runtime.totalMemory() - runtime.freeMemory());
                    log.info("Memory freed: {} MB", Math.round((freedMemory / 1024d / 1024d)));
                    break;
                }
                case "status": {
                    val runtime = Runtime.getRuntime();
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    var metric = UnpooledByteBufAllocator.DEFAULT.metric();
                    log.info("Unpooled Netty Heap Memory Used: {} KB", Math.round(metric.usedHeapMemory() / 1024D));
                    log.info("Unpooled Netty Direct Memory Used: {} KB", Math.round(metric.usedDirectMemory() / 1024D));
                    metric = PooledByteBufAllocator.DEFAULT.metric();
                    log.info("Pooled Netty Heap Memory Used: {} KB", Math.round(metric.usedHeapMemory() / 1024D));
                    log.info("Pooled Netty Direct Memory Used: {} KB", Math.round(metric.usedDirectMemory() / 1024D));
                    metric = ((AdaptiveByteBufAllocator) AdaptiveByteBufAllocator.DEFAULT).metric();
                    log.info("Adaptive Netty Heap Memory Used: {} KB", Math.round(metric.usedHeapMemory() / 1024D));
                    log.info("Adaptive Netty Direct Memory Used: {} KB", Math.round(metric.usedDirectMemory() / 1024D));

                    log.info("Total memory: {} MB", Math.round((runtime.totalMemory() / 1024d / 1024d)));
                    log.info("Memory used: {} MB", Math.round((usedMemory / 1024d / 1024d)));
                    break;
                }
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
        this.running.set(false);

        val iter = OuranosProxySession.ouranosPlayers.iterator();
        while (iter.hasNext()) {
            val player = iter.next();
            iter.remove();
            player.disconnect("Ouranos closed.");
            player.upstream.getPeer().getChannel().closeFuture().syncUninterruptibly();
            player.downstream.getPeer().getChannel().closeFuture().syncUninterruptibly();
        }

        this.bossGroup.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MS, SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS).sync();
        this.workerGroup.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MS, SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS).sync();
    }

    public Bootstrap prepareUpstreamBootstrap() {
        if (this.config.proxy_protocol) {
            return new Bootstrap().group(this.workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_RCVBUF, 128)
                    .option(ChannelOption.SO_SNDBUF, 128);
        }
        return this.preparePingBootstrap();
    }

    public Bootstrap preparePingBootstrap() {
        return new Bootstrap().group(this.workerGroup)
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(RakChannelOption.RAK_COMPATIBILITY_MODE, true)
                .option(RakChannelOption.RAK_AUTO_FLUSH, true)
                .option(RakChannelOption.RAK_FLUSH_INTERVAL, 10);
    }

    public static void main(String[] args) {
        val main = new Ouranos();
        main.start();
    }
}
