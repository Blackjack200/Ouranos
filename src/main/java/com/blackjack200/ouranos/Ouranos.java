package com.blackjack200.ouranos;

import com.blackjack200.ouranos.network.ProtocolInfo;
import com.blackjack200.ouranos.network.cache.StaticPackets;
import com.blackjack200.ouranos.network.mapping.ItemTypeDictionary;
import com.blackjack200.ouranos.network.translate.Translate;
import com.blackjack200.ouranos.utils.Port;
import com.blackjack200.ouranos.utils.YamlConfig;
import com.nukkitx.protocol.bedrock.*;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
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
                clientSession.addDisconnectHandler((reason) -> {
                    log.info("{} disconnected due to {}", clientSession.getAddress(), reason);
                });
                clientSession.setPacketHandler(new BedrockPacketHandler() {
                    @Override
                    public boolean handle(LoginPacket packet) {
                        clientSession.setPacketCodec(ProtocolInfo.getPacketCodec(packet.getProtocolVersion()));
                        val clientCodec = clientSession.getPacketCodec();
                        log.info("{} log-in using minecraft {} {}", clientSession.getAddress(), clientCodec.getMinecraftVersion(), clientCodec.getProtocolVersion());

                        val remoteClient = newClient();

                        remoteClient.connect(Ouranos.this.config.getRemote()).whenComplete((remoteSession, throwable) -> {
                            if (throwable != null) {
                                log.error("connecting server {}", throwable.toString());
                                return;
                            }

                            remoteSession.addDisconnectHandler((reason) -> clientSession.disconnect(reason.toString()));
                            clientSession.addDisconnectHandler((reason) -> remoteSession.disconnect());
                            remoteSession.setPacketCodec(serverCodec);
                            remoteSession.setBatchHandler((session12, compressed, packets) -> {
                                Collection<BedrockPacket> newCollection = new ArrayList<>();
                                for (BedrockPacket pk : packets) {
                                    pk = Translate.translate(
                                            remoteSession.getPacketCodec().getProtocolVersion(),
                                            clientCodec.getProtocolVersion(),
                                            pk
                                    );
                                    if (pk instanceof StartGamePacket) {
                                        val p = (StartGamePacket) pk;
                                        p.setVanillaVersion(clientCodec.getMinecraftVersion());
                                        p.getItemEntries().clear();
                                        ItemTypeDictionary.getInstance().getEntries(clientCodec.getProtocolVersion()).forEach((id, info) -> p.getItemEntries().add(new StartGamePacket.ItemEntry(id, (short) info.runtime_id, info.component_based)));
                                        log.info(pk);
                                    }
                                    if (pk instanceof AvailableEntityIdentifiersPacket) {
                                        pk = StaticPackets.getInstance().getActorIdsPacket(clientCodec.getProtocolVersion());
                                    }
                                    if (pk instanceof BiomeDefinitionListPacket) {
                                        pk = StaticPackets.getInstance().biomeDefinition(clientCodec.getProtocolVersion());
                                    }
                                    log.info("-> {}", pk.toString());

                                       newCollection.add(pk);

                                }
                                clientSession.sendWrapped(newCollection, true, true);
                            });
                            clientSession.setBatchHandler((session1, compressed, packets) -> {
                                Collection<BedrockPacket> newCollection = new ArrayList<>();
                                for (BedrockPacket pk : packets) {
                                    pk = Translate.translate(
                                            clientCodec.getProtocolVersion(),
                                            remoteSession.getPacketCodec().getProtocolVersion(),
                                            pk
                                    );
                                    if (pk instanceof ResourcePackClientResponsePacket){
                                        log.info(pk);
                                    }
                                    log.info("<- {} {}", pk.getClass(), pk.toString());
                                    if (clientCodec.getPacketDefinition(pk.getPacketId()) != null) {
                                        newCollection.add(pk);
                                    }
                                }
                                remoteSession.sendWrapped(newCollection, true, true);
                            });

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
