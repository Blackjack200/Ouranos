package com.github.blackjack200.ouranos.network;

import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v388.Bedrock_v388;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;
import org.cloudburstmc.protocol.bedrock.codec.v390.Bedrock_v390;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v422.Bedrock_v422;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.codec.v431.Bedrock_v431;
import org.cloudburstmc.protocol.bedrock.codec.v440.Bedrock_v440;
import org.cloudburstmc.protocol.bedrock.codec.v448.Bedrock_v448;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.bedrock.codec.v471.Bedrock_v471;
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
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public final class ProtocolInfo {
    private static final Set<BedrockCodec> PACKET_CODECS = ConcurrentHashMap.newKeySet();
    private static final Set<BedrockCodec> UNMODIFIABLE_PACKET_CODECS = Collections.unmodifiableSet(PACKET_CODECS);

    static {
        // 1.21.x
        addPacketCodec(Bedrock_v898.CODEC, 251);
        addPacketCodec(Bedrock_v860.CODEC, 251);
        addPacketCodec(Bedrock_v859.CODEC, 251);
        addPacketCodec(Bedrock_v844.CODEC, 251);
        addPacketCodec(Bedrock_v827.CODEC, 241);
        addPacketCodec(Bedrock_v819.CODEC, 231);
        addPacketCodec(Bedrock_v818.CODEC, 231);
        addPacketCodec(Bedrock_v800.CODEC, 231);
        addPacketCodec(Bedrock_v786.CODEC, 231);
        addPacketCodec(Bedrock_v776.CODEC, 231);
        addPacketCodec(Bedrock_v766.CODEC, 231);
        addPacketCodec(Bedrock_v748.CODEC, 221);
        addPacketCodec(Bedrock_v729.CODEC, 211);
        addPacketCodec(Bedrock_v712.CODEC, 201);
        addPacketCodec(Bedrock_v686.CODEC, 201);
        addPacketCodec(Bedrock_v685.CODEC, 191);
        // 1.20.x
        addPacketCodec(Bedrock_v671.CODEC, 181);
        addPacketCodec(Bedrock_v662.CODEC, 171);
        addPacketCodec(Bedrock_v649.CODEC, 161);
        addPacketCodec(Bedrock_v630.CODEC, 151);
        addPacketCodec(Bedrock_v622.CODEC, 141);
        addPacketCodec(Bedrock_v618.CODEC, 131);
        addPacketCodec(Bedrock_v594.CODEC, 121);
        //TODO add v589 support back
        //addPacketCodec(Bedrock_v589.CODEC);

        // 1.19.x
        addPacketCodec(Bedrock_v582.CODEC, 101);
        addPacketCodec(Bedrock_v575.CODEC, 91);
        addPacketCodec(Bedrock_v568.CODEC, 81);
        addPacketCodec(Bedrock_v567.CODEC, 81);

        addPacketCodec(Bedrock_v560.CODEC, 81);

        addPacketCodec(Bedrock_v557.CODEC, 81);
        addPacketCodec(Bedrock_v554.CODEC, 81);

        addPacketCodec(Bedrock_v545.CODEC, 71);
        addPacketCodec(Bedrock_v544.CODEC, 71);
        addPacketCodec(Bedrock_v534.CODEC, 71);
        addPacketCodec(Bedrock_v527.CODEC, 71);

        // 1.18.x
        addPacketCodec(Bedrock_v503.CODEC, 71);
        addPacketCodec(Bedrock_v486.CODEC, 61);
        addPacketCodec(Bedrock_v475.CODEC, 51);

        // 1.17.x
        addPacketCodec(Bedrock_v471.CODEC, 41);
        addPacketCodec(Bedrock_v465.CODEC, 31);
        addPacketCodec(Bedrock_v448.CODEC, 31);
        addPacketCodec(Bedrock_v440.CODEC, 31);

        // 1.16.x
        addPacketCodec(Bedrock_v431.CODEC, 31);
        addPacketCodec(Bedrock_v428.CODEC, 31);
        addPacketCodec(Bedrock_v422.CODEC, 31);
        addPacketCodec(Bedrock_v419.CODEC, 21);
        //1.16.40 partially playable
        addPacketCodec(Bedrock_v408.CODEC, 11);
        addPacketCodec(Bedrock_v407.CODEC, 11);

        // 1.14.x partially playable
        addPacketCodec(Bedrock_v390.CODEC, 11);
        addPacketCodec(Bedrock_v389.CODEC, 11);

        // 1.13.0 partially playable
        addPacketCodec(Bedrock_v388.CODEC, 11);

        // 1.12.0 partially playable
        addPacketCodec(Bedrock_v361.CODEC, 11);

        // 1.11.0 not playable
        //addPacketCodec(Bedrock_v354.CODEC, 1);
        /*addPacketCodec(Bedrock_v340.CODEC, 21);
        addPacketCodec(Bedrock_v332.CODEC, 21);
        addPacketCodec(Bedrock_v313.CODEC, 21);
        addPacketCodec(Bedrock_v291.CODEC, 21);*/
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