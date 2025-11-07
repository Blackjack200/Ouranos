package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import java.util.ArrayList;
import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.*;

public final class BlockTranslationStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }
        var input = context.inputProtocol();
        var output = context.outputProtocol();

        if (packet instanceof UpdateBlockPacket pk) {
            var runtimeId = pk.getDefinition().getRuntimeId();
            var translated = TypeConverter.translateBlockRuntimeId(
                input,
                output,
                runtimeId
            );
            pk.setDefinition(new SimpleBlockDefinition(translated));
        } else if (packet instanceof LevelEventPacket pk) {
            translateLevelEvent(input, output, packets, pk);
        } else if (packet instanceof LevelSoundEventPacket pk) {
            translateSoundEvent(input, output, packets, pk);
        } else if (packet instanceof EntityEventPacket pk) {
            if (pk.getType() == EntityEventType.EATING_ITEM) {
                var data = pk.getData();
                var newItem = TypeConverter.translateItemRuntimeId(
                    input,
                    output,
                    data >> 16,
                    data & 0xFFFF
                );
                pk.setData((newItem[0] << 16) | newItem[1]);
            }
        } else if (packet instanceof AddEntityPacket pk) {
            if ("minecraft:falling_block".equals(pk.getIdentifier())) {
                var metaData = pk.getMetadata();
                int runtimeId = metaData.get(EntityDataTypes.VARIANT);
                metaData.put(
                    EntityDataTypes.VARIANT,
                    TypeConverter.translateBlockRuntimeId(
                        input,
                        output,
                        runtimeId
                    )
                );
                pk.setMetadata(metaData);
            }
        } else if (packet instanceof UpdateSubChunkBlocksPacket pk) {
            translateSubChunkBlocks(input, output, pk);
        }
    }

    private static void translateLevelEvent(
        int input,
        int output,
        Collection<BedrockPacket> packets,
        LevelEventPacket packet
    ) {
        var type = packet.getType();
        var data = packet.getData();

        if (type == ParticleType.ICON_CRACK) {
            var newItem = TypeConverter.translateItemRuntimeId(
                input,
                output,
                data >> 16,
                data & 0xFFFF
            );
            data = (newItem[0] << 16) | newItem[1];
        } else if (type == LevelEvent.PARTICLE_DESTROY_BLOCK) {
            data = TypeConverter.translateBlockRuntimeId(input, output, data);
        } else if (type == LevelEvent.PARTICLE_CRACK_BLOCK) {
            var face = data >> 24;
            var runtimeId = data & ~(face << 24);
            data =
                TypeConverter.translateBlockRuntimeId(
                    input,
                    output,
                    runtimeId
                ) |
                (face << 24);
        }
        packet.setData(data);
    }

    private static void translateSoundEvent(
        int input,
        int output,
        Collection<BedrockPacket> packets,
        LevelSoundEventPacket packet
    ) {
        var sound = packet.getSound();
        var runtimeId = packet.getExtraData();
        switch (sound) {
            case
                DOOR_OPEN,
                DOOR_CLOSE,
                TRAPDOOR_OPEN,
                TRAPDOOR_CLOSE,
                FENCE_GATE_OPEN,
                FENCE_GATE_CLOSE -> {
                packet.setExtraData(
                    TypeConverter.translateBlockRuntimeId(
                        input,
                        output,
                        runtimeId
                    )
                );
                if (output < Bedrock_v560.CODEC.getProtocolVersion()) {
                    packets.clear();
                    var replacement = new LevelEventPacket();
                    replacement.setType(LevelEvent.SOUND_DOOR_OPEN);
                    replacement.setPosition(packet.getPosition());
                    packets.add(replacement);
                }
            }
            case PLACE, BREAK_BLOCK, ITEM_USE_ON -> packet.setExtraData(
                TypeConverter.translateBlockRuntimeId(input, output, runtimeId)
            );
            default -> {}
        }
    }

    private static void translateSubChunkBlocks(
        int input,
        int output,
        UpdateSubChunkBlocksPacket packet
    ) {
        var newExtraBlocks = new ArrayList<BlockChangeEntry>(
            packet.getExtraBlocks().size()
        );
        for (var entry : packet.getExtraBlocks()) {
            newExtraBlocks.add(
                new BlockChangeEntry(
                    entry.getPosition(),
                    TypeConverter.translateBlockDefinition(
                        input,
                        output,
                        entry.getDefinition()
                    ),
                    entry.getUpdateFlags(),
                    entry.getMessageEntityId(),
                    entry.getMessageType()
                )
            );
        }
        packet.getExtraBlocks().clear();
        packet.getExtraBlocks().addAll(newExtraBlocks);

        var newStandardBlock = new ArrayList<BlockChangeEntry>(
            packet.getStandardBlocks().size()
        );
        for (var entry : packet.getStandardBlocks()) {
            newStandardBlock.add(
                new BlockChangeEntry(
                    entry.getPosition(),
                    TypeConverter.translateBlockDefinition(
                        input,
                        output,
                        entry.getDefinition()
                    ),
                    entry.getUpdateFlags(),
                    entry.getMessageEntityId(),
                    entry.getMessageType()
                )
            );
        }
        packet.getStandardBlocks().clear();
        packet.getStandardBlocks().addAll(newStandardBlock);
    }
}
