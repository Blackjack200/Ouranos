package com.blackjack200.ouranos;

import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.utils.Port;
import com.blackjack200.ouranos.utils.YamlConfig;
import com.nukkitx.protocol.bedrock.*;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class Ouranos {
    private final ServerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Ouranos() {
        this.config = new ServerConfig(new YamlConfig(new File("config.yml")));
    }

    private void start() {
        BedrockServer server = new BedrockServer(this.config.getBind());
        val pong = this.config.getPong();
        val serverCodec = ProtocolInfo.getPacketCodec(pong.getProtocolVersion());
        assert serverCodec != null;

        log.info("Remote server codec: {}", serverCodec.getProtocolVersion());
        server.setHandler(new BedrockServerEventHandler() {
            @Override
            public boolean onConnectionRequest(InetSocketAddress address) {
                return true;
            }

            @Override
            public BedrockPong onQuery(InetSocketAddress address) {
                return pong;
            }

            @Override
            public void onSessionCreation(BedrockServerSession clientSession) {
                log.info("{} connected", clientSession.getAddress());
                clientSession.addDisconnectHandler((reason) -> log.info("{} disconnected due to {}", clientSession.getAddress(), reason));
                clientSession.setPacketHandler(new BedrockPacketHandler() {
                    @Override
                    public boolean handle(LoginPacket packet) {
                        clientSession.setPacketCodec(ProtocolInfo.getPacketCodec(packet.getProtocolVersion()));
                        log.info("{} log-in using minecraft {} {}", clientSession.getAddress(), clientSession.getPacketCodec().getMinecraftVersion(), clientSession.getPacketCodec().getProtocolVersion());

                        val remoteClient = newClient();

                        remoteClient.connect(Ouranos.this.config.getRemote()).whenComplete((remoteSession, throwable) -> {
                            if (throwable != null) {
                                log.error("connecting server {}", throwable.toString());
                                return;
                            }

                            remoteSession.addDisconnectHandler((reason) -> clientSession.disconnect(reason.toString()));
                            remoteSession.setPacketCodec(serverCodec);
                            remoteSession.setBatchHandler((session12, compressed, packets) -> clientSession.sendWrapped(packets, true));
                            clientSession.setBatchHandler((session1, compressed, packets) -> remoteSession.sendWrapped(packets, true));

                            packet.setProtocolVersion(serverCodec.getProtocolVersion());
                            remoteSession.sendPacket(packet);
                        });
                        return true;
                    }
                });
            }
        });
        server.bind().join();
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

    private BedrockClient newClient() {
        val client = new BedrockClient(Port.allocateAddr());
        client.bind().join();
        return client;
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
}
