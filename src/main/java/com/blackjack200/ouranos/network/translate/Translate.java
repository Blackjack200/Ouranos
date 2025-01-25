package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.Arrays;
import java.util.Collection;

@Log4j2
public class Translate {

    public static BedrockPacket translate(int source, int destination, BedrockPacket p) {
        var pk = rewriteBlock(source, destination, p);
        if (pk != null) {
            return pk;
        }
        return p;
    }

    private static BedrockPacket rewriteBlock(int source, int destination, BedrockPacket p) {
        if (p instanceof LevelChunkPacket packet) {
            var from = packet.getData();
            var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());

            var success = rewriteChunkData(source, destination, from, to, packet.getSubChunksLength());
            if (success) {
                packet.setData(to);
                ReferenceCountUtil.release(from);
            }
            return packet;
        }
        if (p instanceof UpdateBlockPacket packet) {
            var runtimeId = packet.getDefinition().getRuntimeId();
            var translated = translateBlockRuntimeId(source, destination, runtimeId);
            packet.setDefinition(() -> translated);
        }
        if (p instanceof LevelEventPacket packet) {
            var type = packet.getType();
            if (type != ParticleType.TERRAIN && type != LevelEvent.PARTICLE_DESTROY_BLOCK && type != LevelEvent.PARTICLE_CRACK_BLOCK) {
                return packet;
            }
            var data = packet.getData();
            var high = data & 0xFFFF0000;
            var blockID = translateBlockRuntimeId(source, destination, data & 0xFFFF) & 0xFFFF;
            packet.setData(high | blockID);
        }
        if (p instanceof LevelSoundEventPacket packet) {
            if (packet.getSound() == SoundEvent.PLACE || packet.getSound() == SoundEvent.BREAK) {
                var runtimeId = packet.getExtraData();
                packet.setExtraData(translateBlockRuntimeId(source, destination, runtimeId));
            }
        }
        if (p instanceof AddEntityPacket packet) {
            if (packet.getIdentifier().equals("minecraft:falling_block")) {
                var metaData = packet.getMetadata();
                int runtimeId = metaData.get(EntityDataTypes.VARIANT);
                metaData.put(EntityDataTypes.VARIANT, translateBlockRuntimeId(source, destination, runtimeId));
            }
        }
        return null;
    }

    private static boolean rewriteChunkData(int source, int destination, ByteBuf from, ByteBuf to, int sections) {
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
                            VarInts.writeInt(to, translateBlockRuntimeId(source, destination, runtimeId));
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

    public static int translateBlockRuntimeId(int source, int destination, int blockRuntimeId) {
        val internalStateId = RuntimeBlockMapping.getInstance().fromRuntimeId(source, blockRuntimeId);
        int fallback = RuntimeBlockMapping.getInstance().getFallback(destination);
        if (internalStateId == null) {
            return fallback;
        }
        val converted = RuntimeBlockMapping.getInstance().toRuntimeId(destination, internalStateId);
        if (converted == null) {

            return fallback;
        }
        return converted;
    }


    private static ItemData translateItemStack(int source, int destination, ItemData oldStack) {
        if (!oldStack.isValid()) {
            return oldStack;
        }
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
            int translated = translateBlockRuntimeId(source, destination, oldStack.getBlockDefinition().getRuntimeId());
            newData.blockDefinition(() -> translated);
        }
        return newData.build();
    }
}