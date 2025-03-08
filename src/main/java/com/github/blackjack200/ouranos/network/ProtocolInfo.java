package com.github.blackjack200.ouranos.network;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v486.Bedrock_v486;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v545.Bedrock_v545;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v557.Bedrock_v557;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v567.Bedrock_v567;
import org.cloudburstmc.protocol.bedrock.codec.v568.Bedrock_v568;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class ProtocolInfo {
    private static final Set<BedrockCodec> PACKET_CODECS = ConcurrentHashMap.newKeySet();
    private static final Set<BedrockCodec> UNMODIFIABLE_PACKET_CODECS = Collections.unmodifiableSet(PACKET_CODECS);

    static {
        // 1.21.x
        addPacketCodec(Bedrock_v776.CODEC, 321);
        addPacketCodec(Bedrock_v766.CODEC, 311);
        addPacketCodec(Bedrock_v748.CODEC, 311);
        addPacketCodec(Bedrock_v729.CODEC, 311);
        addPacketCodec(Bedrock_v712.CODEC, 301);
        addPacketCodec(Bedrock_v686.CODEC, 291);
        addPacketCodec(Bedrock_v685.CODEC, 291);
        // 1.20.x
        addPacketCodec(Bedrock_v671.CODEC, 281);
        addPacketCodec(Bedrock_v662.CODEC, 271);
        addPacketCodec(Bedrock_v649.CODEC, 261);
        addPacketCodec(Bedrock_v630.CODEC, 251);
        addPacketCodec(Bedrock_v622.CODEC, 241);
        addPacketCodec(Bedrock_v618.CODEC, 231);
        addPacketCodec(Bedrock_v594.CODEC, 211);
        //TODO add v589 support back
        //addPacketCodec(Bedrock_v589.CODEC);

        // 1.19.x
        addPacketCodec(Bedrock_v582.CODEC, 191);
        addPacketCodec(Bedrock_v575.CODEC, 181);
        addPacketCodec(Bedrock_v568.CODEC, 171);
        addPacketCodec(Bedrock_v567.CODEC, 171);

        addPacketCodec(Bedrock_v560.CODEC, 161);

        addPacketCodec(Bedrock_v557.CODEC, 151);
        addPacketCodec(Bedrock_v554.CODEC, 151);

        addPacketCodec(Bedrock_v545.CODEC, 151);
        addPacketCodec(Bedrock_v544.CODEC, 151);
        addPacketCodec(Bedrock_v534.CODEC, 151);
        addPacketCodec(Bedrock_v527.CODEC, 151);

        // 1.18.x
        addPacketCodec(Bedrock_v503.CODEC, 141);
        addPacketCodec(Bedrock_v486.CODEC, 131);
        addPacketCodec(Bedrock_v475.CODEC, 121);
    }

    public static BedrockCodec getPacketCodec(int protocolVersion) {
        for (var packetCodec : PACKET_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    public static void addPacketCodec(BedrockCodec packetCodec, int schemaId) {
        PACKET_CODECS.add(packetCodec);
        GlobalItemDataHandlers.SCHEMA_ID.put(packetCodec.getProtocolVersion(), schemaId);
    }

    public static Set<BedrockCodec> getPacketCodecs() {
        return UNMODIFIABLE_PACKET_CODECS;
    }
}