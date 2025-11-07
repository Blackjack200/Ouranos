package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;

/**
 * Handles direction-agnostic packet tweaks that should always be applied
 * before deeper translation (e.g., forcing resource pack version wildcards,
 * disabling unsafe cache support).
 */
public final class PacketNormalizationStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (packet instanceof ResourcePackStackPacket pk) {
            pk.setGameVersion("*");
        } else if (packet instanceof ClientCacheStatusPacket pk) {
            pk.setSupported(false);
        }
    }
}
