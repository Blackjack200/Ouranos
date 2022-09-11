package com.blackjack200.ouranos.network;

import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.v419.Bedrock_v419;
import com.nukkitx.protocol.bedrock.v422.Bedrock_v422;
import com.nukkitx.protocol.bedrock.v428.Bedrock_v428;
import com.nukkitx.protocol.bedrock.v431.Bedrock_v431;
import com.nukkitx.protocol.bedrock.v440.Bedrock_v440;
import com.nukkitx.protocol.bedrock.v448.Bedrock_v448;
import com.nukkitx.protocol.bedrock.v465.Bedrock_v465;
import com.nukkitx.protocol.bedrock.v471.Bedrock_v471;
import com.nukkitx.protocol.bedrock.v475.Bedrock_v475;
import com.nukkitx.protocol.bedrock.v486.Bedrock_v486;
import com.nukkitx.protocol.bedrock.v503.Bedrock_v503;
import com.nukkitx.protocol.bedrock.v527.Bedrock_v527;
import com.nukkitx.protocol.bedrock.v534.Bedrock_v534;
import com.nukkitx.protocol.bedrock.v544.Bedrock_v544;
import com.nukkitx.protocol.bedrock.v545.Bedrock_v545;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@ParametersAreNonnullByDefault
public final class ProtocolInfo {
    private static final Set<BedrockPacketCodec> PACKET_CODECS = ConcurrentHashMap.newKeySet();
    private static final Set<BedrockPacketCodec> UNMODIFIABLE_PACKET_CODECS = Collections.unmodifiableSet(PACKET_CODECS);

    static {
        addPacketCodec(Bedrock_v545.V545_CODEC);
        addPacketCodec(Bedrock_v544.V544_CODEC);
        addPacketCodec(Bedrock_v534.V534_CODEC);
        addPacketCodec(Bedrock_v527.V527_CODEC);
        addPacketCodec(Bedrock_v503.V503_CODEC);
        addPacketCodec(Bedrock_v486.V486_CODEC);
        addPacketCodec(Bedrock_v475.V475_CODEC);
        addPacketCodec(Bedrock_v471.V471_CODEC);
        addPacketCodec(Bedrock_v465.V465_CODEC);
        addPacketCodec(Bedrock_v448.V448_CODEC);
        addPacketCodec(Bedrock_v440.V440_CODEC);
        addPacketCodec(Bedrock_v428.V428_CODEC);
        addPacketCodec(Bedrock_v422.V422_CODEC);
        addPacketCodec(Bedrock_v419.V419_CODEC);
    }

    private static BedrockPacketCodec DEFAULT_PACKET_CODEC;

    static {
        setDefaultPacketCodec(Bedrock_v431.V431_CODEC);
    }

    public static BedrockPacketCodec getDefaultPacketCodec() {
        return DEFAULT_PACKET_CODEC;
    }

    public static void setDefaultPacketCodec(BedrockPacketCodec packetCodec) {
        DEFAULT_PACKET_CODEC = packetCodec;
        PACKET_CODECS.add(DEFAULT_PACKET_CODEC);
    }

    public static String getDefaultMinecraftVersion() {
        return DEFAULT_PACKET_CODEC.getMinecraftVersion();
    }

    public static int getDefaultProtocolVersion() {
        return DEFAULT_PACKET_CODEC.getProtocolVersion();
    }

    @Nullable
    public static BedrockPacketCodec getPacketCodec(@Nonnegative int protocolVersion) {
        for (BedrockPacketCodec packetCodec : PACKET_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    public static void addPacketCodec(BedrockPacketCodec packetCodec) {
        PACKET_CODECS.add(packetCodec);
    }

    public static Set<BedrockPacketCodec> getPacketCodecs() {
        return UNMODIFIABLE_PACKET_CODECS;
    }
}