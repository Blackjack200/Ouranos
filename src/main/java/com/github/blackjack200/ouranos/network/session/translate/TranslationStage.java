package com.github.blackjack200.ouranos.network.session.translate;

import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

@FunctionalInterface
public interface TranslationStage {
    void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    );
}
