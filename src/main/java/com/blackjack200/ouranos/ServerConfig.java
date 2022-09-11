package com.blackjack200.ouranos;

import cn.hutool.log.LogFactory;
import com.blackjack200.ouranos.utils.Config;
import com.blackjack200.ouranos.utils.Port;
import com.nukkitx.protocol.bedrock.BedrockClient;
import com.nukkitx.protocol.bedrock.BedrockPong;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

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
        BedrockClient client = new BedrockClient(Port.allocateAddr());
        client.bind().join();
        try {
            LogFactory.get().info("addr {}", getRemote());
            BedrockPong pong = client.ping(getRemote()).get();
            pong.setIpv4Port(this.getLocalPort());
            pong.setSubMotd("Ouranos");
            client.close();
            return Objects.requireNonNull(pong);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
