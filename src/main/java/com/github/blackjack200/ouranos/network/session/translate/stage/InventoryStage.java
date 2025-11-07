package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.InventoryTranslator;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public final class InventoryStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        InventoryTranslator.rewriteInventory(
            context.inputProtocol(),
            context.outputProtocol(),
            context.fromServer(),
            context.session(),
            packet,
            packets
        );
    }
}
