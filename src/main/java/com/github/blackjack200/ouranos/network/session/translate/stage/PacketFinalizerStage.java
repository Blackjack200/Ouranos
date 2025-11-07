package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityFallPacket;

/**
 * Drops packets that would crash legacy clients once the heavy translation work
 * is done.
 */
public final class PacketFinalizerStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        var output = context.outputProtocol();
        if (
            output >= Bedrock_v554.CODEC.getProtocolVersion() &&
            packet instanceof AdventureSettingsPacket
        ) {
            packets.remove(packet);
        }
        if (
            output > Bedrock_v408.CODEC.getProtocolVersion() &&
            packet instanceof EntityFallPacket
        ) {
            packets.remove(packet);
        }
    }
}
