package com.blackjack200.ouranos.network.translate;

import com.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.blackjack200.ouranos.network.convert.RuntimeBlockMapping;
import com.blackjack200.ouranos.network.session.OuranosPlayer;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
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
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
public class Translate {

    public static BedrockPacket translate(int source, int destination, OuranosPlayer player, BedrockPacket p) {
        if (p instanceof ResourcePackStackPacket pk) {
            pk.setGameVersion("*");
        }

        rewriteProtocol(source, destination, p);
        rewriteBlock(source, destination, p);
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
            if (p instanceof InventoryTransactionPacket pk) {
                pk.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                pk.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
            }
            if (p instanceof MobArmorEquipmentPacket pk) {
                pk.setBody(ItemData.AIR);
            }
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setRawMoveVector(provider.createVector2f(0, 0));
                var transaction = pk.getItemUseTransaction();
                if (transaction != null) {
                    transaction.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                    transaction.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
                }
            }
            if (p instanceof ItemStackRequestPacket pk) {
                val newRequests = new ArrayList<ItemStackRequest>(pk.getRequests().size());
                for (val req : pk.getRequests()) {
                    val newActions = new ArrayList<ItemStackRequestAction>(pk.getRequests().size());
                    var actions = req.getActions();
                    for (int i = 0, iMax = actions.length; i < iMax; i++) {
                        val action = actions[i];
                        if (action instanceof TakeAction a) {
                            newActions.add(new TakeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        } else if (action instanceof ConsumeAction a) {
                            newActions.add(new ConsumeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        } else if (action instanceof DestroyAction a) {
                            newActions.add(new DestroyAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        } else if (action instanceof DropAction a) {
                            newActions.add(new DropAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), a.isRandomly()));
                        } else if (action instanceof PlaceAction a) {
                            val newAct = new PlaceAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination()));
                            // if (newAct.getSource().getContainerName().getContainer().equals(ContainerSlotType.CREATED_OUTPUT)) {
                            //     newActions.add(new CraftCreativeAction(newAct.getSource().getContainerName().getContainer().ordinal(), 1));
                            // }
                            newActions.add(newAct);
                        } else if (action instanceof SwapAction a) {
                            newActions.add(new SwapAction(translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        } else {
                            newActions.add(action);
                        }
                    }
                    newRequests.add(new ItemStackRequest(req.getRequestId(), newActions.toArray(new ItemStackRequestAction[0]), req.getFilterStrings()));
                }
                pk.getRequests().clear();
                pk.getRequests().addAll(newRequests);
            }
        }
        removeNewEntityData(p, destination, Bedrock_v685.CODEC, EntityDataTypes.VISIBLE_MOB_EFFECTS);
        removeNewEntityData(p, destination, Bedrock_v594.CODEC,
                EntityDataTypes.COLLISION_BOX, EntityDataTypes.PLAYER_HAS_DIED, EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION, EntityDataTypes.PLAYER_LAST_DEATH_POS
        );
        removeNewEntityData(p, destination, Bedrock_v503.CODEC,
                EntityDataTypes.HEARTBEAT_SOUND_EVENT, EntityDataTypes.HEARTBEAT_INTERVAL_TICKS, EntityDataTypes.MOVEMENT_SOUND_DISTANCE_OFFSET
        );

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
        if (source < Bedrock_v575.CODEC.getProtocolVersion()) {
            if (p instanceof PlayerAuthInputPacket pk) {
                //FIXME? context based value: xuid platformId
                pk.setAnalogMoveVector(provider.createVector2f(0, 0));
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

    private static ItemStackRequestSlotData translateItemStackRequestSlotData(ItemStackRequestSlotData dest) {
        return new ItemStackRequestSlotData(
                dest.getContainer(),
                dest.getSlot(),
                dest.getStackNetworkId(),
                Optional.ofNullable(dest.getContainerName())
                        .orElse(new FullContainerName(dest.getContainer(), 0))
        );
    }

    private static void removeNewEntityData(BedrockPacket p, int destination, BedrockCodec codec, EntityDataType<?>... types) {
        if (destination < codec.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            }
            if (p instanceof AddEntityPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            }
            if (p instanceof AddPlayerPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            }
            if (p instanceof AddItemEntityPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
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
                subChunk.getData().resetReaderIndex();
                if (subChunk.getData().readableBytes() > 0) {
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
                        if(blocksPerWord==0){
                            continue;
                        }
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
                    throw new RuntimeException("PEBlockRewrite: Unknown subchunk format " + version);
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
}