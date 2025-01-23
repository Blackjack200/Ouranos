package com.blackjack200.ouranos;

import com.blackjack200.ouranos.utils.Config;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;

import java.net.InetSocketAddress;

@Log4j2
public class ServerConfig {
    private final Config conf;

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
                .playerCount(0)
                .maximumPlayerCount(20)
                .gameType("Survival")
                .protocolVersion(Bedrock_v389.CODEC.getProtocolVersion());
    }
}
