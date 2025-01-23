package com.blackjack200.ouranos.network;

import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class ProtocolInfo {
    private static final Set<BedrockCodec> PACKET_CODECS = ConcurrentHashMap.newKeySet();
    private static final Set<BedrockCodec> UNMODIFIABLE_PACKET_CODECS = Collections.unmodifiableSet(PACKET_CODECS);

    static {
        addPacketCodec(Bedrock_v766.CODEC);
        addPacketCodec(Bedrock_v686.CODEC);
        addPacketCodec(Bedrock_v685.CODEC);
        addPacketCodec(Bedrock_v671.CODEC);
        addPacketCodec(Bedrock_v662.CODEC);
        addPacketCodec(Bedrock_v649.CODEC);
        addPacketCodec(Bedrock_v630.CODEC);
        addPacketCodec(Bedrock_v622.CODEC);
        addPacketCodec(Bedrock_v618.CODEC);
        addPacketCodec(Bedrock_v594.CODEC);
        addPacketCodec(Bedrock_v589.CODEC);
    }

    private static BedrockCodec DEFAULT_PACKET_CODEC;

    static {
        setDefaultPacketCodec(Bedrock_v685.CODEC);
    }

    public static BedrockCodec getDefaultPacketCodec() {
        return DEFAULT_PACKET_CODEC;
    }

    public static void setDefaultPacketCodec(BedrockCodec packetCodec) {
        DEFAULT_PACKET_CODEC = packetCodec;
        PACKET_CODECS.add(DEFAULT_PACKET_CODEC);
    }

    public static String getDefaultMinecraftVersion() {
        return DEFAULT_PACKET_CODEC.getMinecraftVersion();
    }

    public static int getDefaultProtocolVersion() {
        return DEFAULT_PACKET_CODEC.getProtocolVersion();
    }

    public static BedrockCodec getPacketCodec(int protocolVersion) {
        for (var packetCodec : PACKET_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    public static void addPacketCodec(BedrockCodec packetCodec) {
        PACKET_CODECS.add(packetCodec);
    }

    public static Set<BedrockCodec> getPacketCodecs() {
        return UNMODIFIABLE_PACKET_CODECS;
    }
}