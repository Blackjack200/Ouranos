package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.MovementTranslator;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public final class MovementStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        MovementTranslator.rewriteMovement(
            context.inputProtocol(),
            context.outputProtocol(),
            context.fromServer(),
            context.session(),
            packet,
            packets
        );
    }
}
