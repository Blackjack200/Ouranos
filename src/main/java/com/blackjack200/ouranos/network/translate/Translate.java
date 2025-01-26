package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.UUID;

@Log4j2
public class Translate {

    public static BedrockPacket translate(int source, int destination, BedrockPacket p) {
        if (p instanceof ResourcePackStackPacket pk) {
            pk.setGameVersion("*");
        }
        rewriteProtocol(source, destination, p);
        rewriteBlock(source, destination, p);
        if (p instanceof CreativeContentPacket packet) {
            val newContents = new ArrayList<ItemData>();
            //FIXME
            packet.setContents(newContents.toArray(new ItemData[0]));
            return packet;
        }
        if (p instanceof InventoryContentPacket packet) {
            val newContents = new ArrayList<ItemData>();
            for (int i = 0; i < packet.getContents().size(); i++) {
                ItemData d = translateItemStack(source, destination, packet.getContents().get(i));
                if (d != null) {
                    newContents.add(d);
                }
            }
            packet.setContents(newContents);
            return packet;
        }
        return p;
    }

    private static void rewriteProtocol(int source, int destination, BedrockPacket p) {
        val provider = new ImmutableVectorProvider();
        if (source < Bedrock_v766.CODEC.getProtocolVersion()) {
            if (p instanceof ResourcePacksInfoPacket pk) {
                pk.setWorldTemplateId(UUID.randomUUID());
                pk.setWorldTemplateVersion("0.0.0");
            }
        }
        if (source < Bedrock_v748.CODEC.getProtocolVersion()) {
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setInteractRotation(provider.createVector2f(0, 0));
                pk.setCameraOrientation(provider.createVector3f(0, 0, 0));
            }
        }
        if (source < Bedrock_v729.CODEC.getProtocolVersion()) {
            if (p instanceof TransferPacket pk) {
                pk.setReloadWorld(true);
            }
        }
        if (source < Bedrock_v712.CODEC.getProtocolVersion()) {
            if (p instanceof MobArmorEquipmentPacket pk) {
                pk.setBody(ItemData.AIR);
            }
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setRawMoveVector(provider.createVector2f(0, 0));
                if (!pk.getPlayerActions().isEmpty()) {
                    log.info(pk);
                }
                var transaction = pk.getItemUseTransaction();
                if (transaction != null) {
                    transaction.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                    transaction.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
                }
            }
        }
        if (destination < Bedrock_v685.CODEC.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                pk.getMetadata().remove(EntityDataTypes.VISIBLE_MOB_EFFECTS);
            }
        }
        if (source < Bedrock_v685.CODEC.getProtocolVersion()) {
            if (p instanceof ContainerClosePacket pk) {
                //TODO context based value: container type
                pk.setType(ContainerType.NONE);
            }
        }
        if (source < Bedrock_v671.CODEC.getProtocolVersion()) {
            if (p instanceof ResourcePackStackPacket pk) {
                pk.setHasEditorPacks(false);
            }
        }
        if (source < Bedrock_v649.CODEC.getProtocolVersion()) {
            if (p instanceof LevelChunkPacket pk) {
                //FIXME overworld?
                pk.setDimension(0);
            }
            if (p instanceof PlayerListPacket pk) {
                for (var e : pk.getEntries()) {
                    //FIXME context based value: subclient
                    e.setSubClient(false);
                }
            }
        }
        if (source < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (p instanceof EmotePacket pk) {
                //FIXME? context based value: xuid platformId
                pk.setXuid("");
                pk.setPlatformId("");
                pk.setEmoteDuration(20);
            }
        }

        if (destination < Bedrock_v594.CODEC.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                pk.getMetadata().remove(EntityDataTypes.COLLISION_BOX);
            }
        }
        if (destination < Bedrock_v527.CODEC.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                pk.getMetadata().remove(EntityDataTypes.PLAYER_LAST_DEATH_POS);
                pk.getMetadata().remove(EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION);
                pk.getMetadata().remove(EntityDataTypes.PLAYER_HAS_DIED);
            }
        }
    }

    private static void rewriteBlock(int source, int destination, BedrockPacket p) {
        if (p instanceof LevelChunkPacket packet) {
            var from = packet.getData();
            var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());

            var success = rewriteChunkData(source, destination, from, to, packet.getSubChunksLength());
            if (success) {
                packet.setData(to);
                ReferenceCountUtil.release(from);
            }
            return;
        }
        if (p instanceof SubChunkPacket packet) {
            for (var subChunk : packet.getSubChunks()) {
                if (subChunk.getResult().equals(SubChunkRequestResult.SUCCESS)) {
                    ByteBuf from = subChunk.getData();
                    var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());
                    rewriteChunkData(source, destination, from, to, 1);
                    subChunk.setData(to);
                    ReferenceCountUtil.release(from);
                }
            }
        }
        if (p instanceof UpdateBlockPacket packet) {
            var runtimeId = packet.getDefinition().getRuntimeId();
            var translated = translateBlockRuntimeId(source, destination, runtimeId);
            packet.setDefinition(() -> translated);
            return;
        }
        if (p instanceof LevelEventPacket packet) {
            var type = packet.getType();
            if (type != ParticleType.TERRAIN && type != LevelEvent.PARTICLE_DESTROY_BLOCK && type != LevelEvent.PARTICLE_CRACK_BLOCK) {
                return;
            }
            var data = packet.getData();
            var high = data & 0xFFFF0000;
            var blockID = translateBlockRuntimeId(source, destination, data & 0xFFFF) & 0xFFFF;
            packet.setData(high | blockID);
        }
        if (p instanceof LevelSoundEventPacket pk) {
            if (pk.getSound() == SoundEvent.PLACE || pk.getSound() == SoundEvent.BREAK) {
                var runtimeId = pk.getExtraData();
                pk.setExtraData(translateBlockRuntimeId(source, destination, runtimeId));
            }
            return;
        }
        if (p instanceof AddEntityPacket pk) {
            if (pk.getIdentifier().equals("minecraft:falling_block")) {
                var metaData = pk.getMetadata();
                int runtimeId = metaData.get(EntityDataTypes.VARIANT);
                metaData.put(EntityDataTypes.VARIANT, translateBlockRuntimeId(source, destination, runtimeId));
            }
        }
    }

    private static boolean rewriteChunkData(int source, int destination, ByteBuf from, ByteBuf to, int sections) {
        for (var section = 0; section < sections; section++) {
            var version = from.readUnsignedByte();
            to.writeByte(version);
            switch (version) {
                case 0, 4, 139 -> {
                    to.writeBytes(from);
                    return true;
                }
                case 8, 9 -> { // New form chunk, baked-in palette
                    var storageCount = from.readUnsignedByte();
                    to.writeByte(storageCount);
                    if (version == 9) {
                        to.writeByte(from.readByte());
                    }
                    for (var storage = 0; storage < storageCount; storage++) {
                        var flags = from.readUnsignedByte();
                        var bitsPerBlock = flags >> 1; // isRuntime = (flags & 0x1) != 0
                        if (bitsPerBlock == 0) {
                            continue;
                        }
                        var blocksPerWord = Integer.SIZE / bitsPerBlock;
                        var nWords = ((16 * 16 * 16) + blocksPerWord - 1) / blocksPerWord;

                        to.writeByte(flags);
                        to.writeBytes(from, nWords * Integer.BYTES);

                        var nPaletteEntries = VarInts.readInt(from);
                        VarInts.writeInt(to, nPaletteEntries);

                        for (var i = 0; i < nPaletteEntries; i++) {
                            int runtimeId = VarInts.readInt(from);
                            VarInts.writeInt(to, translateBlockRuntimeId(source, destination, runtimeId));
                        }
                    }
                }
                default -> { // Unsupported
                    log.warn("PEBlockRewrite: Unknown subchunk format {}", version);
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
        var stringId = ItemTypeDictionary.getInstance().fromNumericId(source, oldStack.getDefinition().getRuntimeId());
        log.info(stringId);
        var newId = ItemTypeDictionary.getInstance().fromStringId(destination, stringId);
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