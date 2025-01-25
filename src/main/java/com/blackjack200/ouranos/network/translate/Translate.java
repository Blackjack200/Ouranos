package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.convert.ItemTranslator;
import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import com.blackjack200.ouranos.network.session.DownstreamSession;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;

@Log4j2
public class Translate {
    public static BedrockPacket translate(DownstreamSession session, BedrockPacket p) {
        if (p instanceof LevelChunkPacket packet) {
            ByteBuf from = packet.getData();
            ByteBuf to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());

            boolean success = rewriteChunkData(session, from, to, packet.getSubChunksLength());
            if (success) {
                packet.setData(to);
                ReferenceCountUtil.release(from);
            }
        }
        if (p instanceof CreativeContentPacket packet) {
            var originalProtocolId = session.upstream.getCodec().getProtocolVersion();
            var targetProtocolId = session.getCodec().getProtocolVersion();
            val newContents = new ArrayList<ItemData>();
            for (int i = 0; i < packet.getContents().length; i++) {
                newContents.add(translateItemStack(session, packet.getContents()[i]));
            }
            packet.setContents(newContents.toArray(new ItemData[0]));
            return packet;
        }
        if (p instanceof UpdateBlockPacket packet) {
            int runtimeId = packet.getDefinition().getRuntimeId();
            var definition = session.getPeer().getCodecHelper().getBlockDefinitions().getDefinition(translateBlockRuntimeId(session, runtimeId));
            packet.setDefinition(definition);
        }
        if (p instanceof LevelEventPacket packet) {
            var type = packet.getType();
            if (type != ParticleType.TERRAIN && type != LevelEvent.PARTICLE_DESTROY_BLOCK && type != LevelEvent.PARTICLE_CRACK_BLOCK) {
                return p;
            }
            var data = packet.getData();
            var high = data & 0xFFFF0000;
            var blockID = translateBlockRuntimeId(session, data & 0xFFFF) & 0xFFFF;
            packet.setData(high | blockID);
        }
        if (p instanceof LevelSoundEventPacket packet) {
            if (packet.getSound() == SoundEvent.PLACE || packet.getSound() == SoundEvent.BREAK) {
                var runtimeId = packet.getExtraData();
                packet.setExtraData(translateBlockRuntimeId(session, runtimeId));
            }
        }
        if (p instanceof AddEntityPacket packet) {
            if (packet.getIdentifier().equals("minecraft:falling_block")) {
                var metaData = packet.getMetadata();
                int runtimeId = metaData.get(EntityDataTypes.VARIANT);
                metaData.put(EntityDataTypes.VARIANT, translateBlockRuntimeId(session, runtimeId));
            }
        }
        return p;
    }

    private static boolean rewriteChunkData(DownstreamSession sess, ByteBuf from, ByteBuf to, int sections) {
        for (int section = 0; section < sections; section++) {
            int chunkVersion = from.readUnsignedByte();
            to.writeByte(chunkVersion);

            switch (chunkVersion) {
                // Legacy block ids, no remap needed
                // MiNet uses this format
                case 0, 4, 139 -> {
                    to.writeBytes(from);
                    return true;
                }
                case 8 -> { // New form chunk, baked-in palette
                    int storageCount = from.readUnsignedByte();
                    to.writeByte(storageCount);
                    for (int storage = 0; storage < storageCount; storage++) {
                        int flags = from.readUnsignedByte();
                        int bitsPerBlock = flags >> 1; // isRuntime = (flags & 0x1) != 0
                        int blocksPerWord = Integer.SIZE / bitsPerBlock;
                        int nWords = ((16 * 16 * 16) + blocksPerWord - 1) / blocksPerWord;

                        to.writeByte(flags);
                        to.writeBytes(from, nWords * Integer.BYTES);

                        int nPaletteEntries = VarInts.readInt(from);
                        VarInts.writeInt(to, nPaletteEntries);

                        for (int i = 0; i < nPaletteEntries; i++) {
                            int runtimeId = VarInts.readInt(from);
                            VarInts.writeInt(to, translateBlockRuntimeId(sess, runtimeId));
                        }
                    }
                }
                default -> { // Unsupported
                    log.warn("PEBlockRewrite: Unknown subchunk format {}", chunkVersion);
                    return false;
                }
            }
        }
        return true;
    }

    public static int translateBlockRuntimeId(DownstreamSession sess, int blockRuntimeId) {
        var originalProtocolId = sess.upstream.getCodec().getProtocolVersion();
        var targetProtocolId = sess.getCodec().getProtocolVersion();

        val internalStateId = RuntimeBlockMapping.getInstance().fromRuntimeId(originalProtocolId, blockRuntimeId);
        int fallback = RuntimeBlockMapping.getInstance().getFallback(targetProtocolId);
        if (internalStateId == null) {
            return fallback;
        }
        val converted = RuntimeBlockMapping.getInstance().toRuntimeId(targetProtocolId, internalStateId);
        if (converted == null) {

            return fallback;
        }
        return converted;
    }


    private static ItemData translateItemStack(DownstreamSession session, ItemData oldStack) {
        if (!oldStack.isValid()) {
            return oldStack;
        }
        var originalProtocolId = session.upstream.getCodec().getProtocolVersion();
        var targetProtocolId = session.getCodec().getProtocolVersion();

        var newData = ItemData.builder()
                .definition(oldStack.getDefinition())
                .damage(oldStack.getDamage())
                .count(oldStack.getCount())
                .tag(oldStack.getTag())
                .canPlace(oldStack.getCanPlace())
                .canBreak(oldStack.getCanBreak())
                .blockingTicks(oldStack.getBlockingTicks())
                .usingNetId(oldStack.isUsingNetId())
                .netId(oldStack.getNetId());
        if (oldStack.getBlockDefinition() != null) {
            int translated = translateBlockRuntimeId(session, oldStack.getBlockDefinition().getRuntimeId());
            newData.blockDefinition(() -> translated);
        }
        return newData.build();
    }

    private static int[] translateItem(int originalProtocolId, int targetProtocolId, int itemId, int itemMeta) {
        if (itemId == 0) {
            return new int[]{itemId, itemMeta};
        }
        int[] coreData = ItemTranslator.getInstance().fromNetworkIdNotNull(originalProtocolId, itemId, itemMeta);
        return ItemTranslator.getInstance().toNetworkId(targetProtocolId, coreData[0], coreData[1]);
    }

    private static int translateItemNetworkId(int originalProtocolId, int targetProtocolId, int networkId) {
        if (networkId == 0) {
            return networkId;
        }
        int[] data = ItemTranslator.getInstance().fromNetworkIdNotNull(originalProtocolId, networkId, 0);
        return ItemTranslator.getInstance().toNetworkId(targetProtocolId, data[0], data[1])[0];
    }
}