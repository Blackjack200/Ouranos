package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.Collection;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.*;

public final class EntityMetadataStage implements TranslationStage {

    private record StripRule(BedrockCodec codec, EntityDataType<?>[] types) {}

    private static final StripRule[] RULES = new StripRule[] {
        new StripRule(
            Bedrock_v685.CODEC,
            new EntityDataType<?>[] { EntityDataTypes.VISIBLE_MOB_EFFECTS }
        ),
        new StripRule(
            Bedrock_v594.CODEC,
            new EntityDataType<?>[] {
                EntityDataTypes.COLLISION_BOX,
                EntityDataTypes.PLAYER_HAS_DIED,
                EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION,
                EntityDataTypes.PLAYER_LAST_DEATH_POS,
            }
        ),
        new StripRule(
            Bedrock_v527.CODEC,
            new EntityDataType<?>[] {
                EntityDataTypes.PLAYER_LAST_DEATH_POS,
                EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION,
                EntityDataTypes.PLAYER_HAS_DIED,
            }
        ),
        new StripRule(
            Bedrock_v503.CODEC,
            new EntityDataType<?>[] {
                EntityDataTypes.HEARTBEAT_SOUND_EVENT,
                EntityDataTypes.HEARTBEAT_INTERVAL_TICKS,
                EntityDataTypes.MOVEMENT_SOUND_DISTANCE_OFFSET,
            }
        ),
    };

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }
        var metadata = resolveMetadata(packet);
        if (metadata == null) {
            return;
        }
        var output = context.outputProtocol();
        for (var rule : RULES) {
            if (output < rule.codec().getProtocolVersion()) {
                for (var type : rule.types()) {
                    metadata.remove(type);
                }
            }
        }
    }

    private static Map<EntityDataType<?>, Object> resolveMetadata(
        BedrockPacket packet
    ) {
        if (packet instanceof SetEntityDataPacket pk) {
            return pk.getMetadata();
        } else if (packet instanceof AddEntityPacket pk) {
            return pk.getMetadata();
        } else if (packet instanceof AddPlayerPacket pk) {
            return pk.getMetadata();
        } else if (packet instanceof AddItemEntityPacket pk) {
            return pk.getMetadata();
        }
        return null;
    }
}
