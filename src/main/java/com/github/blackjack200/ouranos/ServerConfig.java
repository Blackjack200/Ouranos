package com.github.blackjack200.ouranos;

import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.session.OuranosPlayer;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;

import java.net.InetSocketAddress;
import java.util.Objects;

@Log4j2
public class ServerConfig {
    public String motd = "My Proxy";
    public String sub_motd = "Ouranos";
    public int default_protocol = 766;

    public String server_ipv4 = "0.0.0.0";
    public short server_port_v4 = 19132;
    public boolean server_ipv6_enabled = true;
    public String server_ipv6 = "::0";
    public short server_port_v6 = 19133;

    public String remote_host = "127.0.0.1";
    public short remote_port = 19135;

    public short maximum_player = 10;

    public boolean online_mode = true;

    public boolean debug = false;

    public InetSocketAddress getBindv4() {
        return new InetSocketAddress(this.server_ipv4, this.server_port_v4);
    }

    public InetSocketAddress getBindv6() {
        return new InetSocketAddress(this.server_ipv6, this.server_port_v6);
    }

    public InetSocketAddress getRemote() {
        return new InetSocketAddress(this.remote_host, this.remote_port);
    }

    public BedrockPong getPong() {
        var codec = getRemoteCodec();
        return new BedrockPong()
                .edition("MCPE")
                .motd(this.motd)
                .subMotd(this.sub_motd)
                .serverId(114514L)
                .playerCount(OuranosPlayer.ouranosPlayers.size())
                .maximumPlayerCount(this.maximum_player)
                .gameType("Survival")
                .version(codec.getMinecraftVersion())
                .protocolVersion(codec.getProtocolVersion())
                .ipv4Port(this.server_port_v4)
                .ipv6Port(this.server_port_v6);
    }

    public BedrockCodec getRemoteCodec() {
        return Objects.requireNonNull(ProtocolInfo.getPacketCodec(this.default_protocol), "Unsupported protocol: " + this.default_protocol);
    }
}
