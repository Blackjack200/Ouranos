package com.blackjack200.ouranos;

import com.blackjack200.ouranos.utils.Config;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;

import java.net.InetSocketAddress;

@Log4j2
public class ServerConfig {
    private final Config conf;
    private String motd = "Ouranos Proxy";
    private String sub_motd = "Ouranos Proxy";

    private String server_ipv4 = "0.0.0.0";
    private short server_port_v4 = 19132;
    private String server_ipv6 = "::0";
    private short server_port_v6 = 19133;

    private boolean online_mode = true;
    private boolean encryption = true;

    public ServerConfig(Config conf) {
        this.conf = conf;
    }

    private int getLocalPort() {
        return this.conf.getInteger("local-port");
    }

    public InetSocketAddress getBind() {
        return new InetSocketAddress(this.conf.getString("local-ip"), this.getLocalPort());
    }

    public InetSocketAddress getRemote() {
        return new InetSocketAddress(this.conf.getString("remote-ip"), this.conf.getInteger("remote-port"));
    }

    public BedrockPong getPong() {
        return new BedrockPong()
                .edition("MCPE")
                .motd("My Server")
                .subMotd("Ouranos")
                .serverId(114514L)
                .nintendoLimited(false)
                .playerCount(0)
                .maximumPlayerCount(20)
                .gameType("Survival")
                .version(Bedrock_v389.CODEC.getMinecraftVersion())
                .protocolVersion(Bedrock_v389.CODEC.getProtocolVersion());
    }
}
